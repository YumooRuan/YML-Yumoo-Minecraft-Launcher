package dev.yml.ui.page;

import dev.yml.core.config.InstanceSettings;
import dev.yml.core.config.Keybinds;
import dev.yml.core.game.LocalVersions;
import dev.yml.core.game.ModScanner;
import dev.yml.ui.AppContext;
import dev.yml.ui.KeyNames;
import dev.yml.ui.Page;
import dev.yml.ui.Ui;
import dev.yml.ui.UiHost;
import dev.yml.ui.Util;
import dev.yml.ui.component.Icon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Top-level key binding page: groups bindings by installed mod, and can push the whole set to
 * other instances in one click.
 */
public final class KeybindsPage implements Page {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane view = new BorderPane();
    private final ComboBox<String> instanceBox = new ComboBox<>();
    private final VBox groups = new VBox(10);
    private final Label status = Ui.label("", "tiny", "faint");

    private final List<LocalVersions.Installed> instances = new ArrayList<>();
    private LocalVersions.Installed current;
    private Path optionsFile;
    private final Map<String, String> binds = new LinkedHashMap<>();
    private final Map<String, Label> valueLabels = new LinkedHashMap<>();
    private final Map<String, Button> editButtons = new LinkedHashMap<>();
    private String capturing;
    private Runnable afterCapture;
    private Scene boundScene;

    public KeybindsPage(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        view.setPadding(new Insets(18, 20, 14, 20));
        HBox header = Ui.row(10, Ui.h1("键位设置"),
                Ui.small("先按加载器分区（Fabric / Forge / NeoForge / Quilt），再按模组列出键位"));
        header.setAlignment(Pos.CENTER_LEFT);

        instanceBox.setPrefWidth(300);
        instanceBox.setOnAction(event -> selectInstance());
        Button reload = new Button("重新读取");
        reload.getStyleClass().addAll("btn", "btn-small");
        reload.setOnAction(event -> load());
        Button applyOthers = new Button("同步到其它版本");
        applyOthers.getStyleClass().addAll("btn", "btn-small");
        applyOthers.setOnAction(event -> applyToOthers());
        Button save = new Button("保存");
        save.getStyleClass().addAll("btn", "btn-primary", "btn-small");
        save.setOnAction(event -> save());
        HBox toolbar = new HBox(10, Ui.label("实例", "small", "muted"), instanceBox,
                Ui.spacer(), reload, applyOthers, save);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(groups);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        view.setTop(new VBox(10, header, toolbar));
        view.setCenter(scroll);
        view.setBottom(status);
        BorderPane.setMargin(status, new Insets(8, 0, 0, 2));

        view.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null && scene != boundScene) {
                boundScene = scene;
                installCapture(scene);
            }
        });
    }

    @Override
    public Node view() {
        return view;
    }

    @Override
    public void onShow() {
        refreshInstances();
    }

    private void refreshInstances() {
        instances.clear();
        instances.addAll(context.versions());
        instanceBox.getItems().setAll(instances.stream().map(LocalVersions.Installed::id).toList());
        LocalVersions.Installed selected = context.selected();
        int index = 0;
        if (selected != null) {
            for (int i = 0; i < instances.size(); i++) {
                if (instances.get(i).id().equals(selected.id())) {
                    index = i;
                    break;
                }
            }
        }
        if (!instances.isEmpty()) {
            instanceBox.getSelectionModel().select(index);
            selectInstance();
        }
    }

    private void selectInstance() {
        int index = instanceBox.getSelectionModel().getSelectedIndex();
        if (index < 0 || index >= instances.size()) {
            return;
        }
        current = instances.get(index);
        context.select(current);
        load();
    }

    private void load() {
        if (current == null) {
            return;
        }
        InstanceSettings settings = context.instanceSettings(current);
        Path gameDir = settings.isolated ? current.dir() : context.settings.mcPath();
        optionsFile = gameDir.resolve("options.txt");
        binds.clear();
        binds.putAll(Keybinds.read(optionsFile));
        groups.getChildren().clear();
        valueLabels.clear();
        editButtons.clear();

        Map<String, String> vanilla = new LinkedHashMap<>();
        for (String action : Keybinds.ACTION_ORDER) {
            vanilla.put(action, binds.getOrDefault(action, ""));
        }
        groups.getChildren().add(groupCard("原版 Minecraft", vanilla, null));

        String instanceLoader = current.loader().name();
        List<ModScanner.ModEntry> mods = ModScanner.scan(gameDir.resolve("mods"));
        Map<String, List<String>> byMod = new LinkedHashMap<>();
        List<String> other = new ArrayList<>();
        for (String action : binds.keySet()) {
            if (Keybinds.ACTION_NAMES.containsKey(action)) {
                continue;
            }
            ModScanner.ModEntry owner = ownerOf(action, mods);
            if (owner == null) {
                other.add(action);
            } else {
                byMod.computeIfAbsent(owner.id(), k -> new ArrayList<>()).add(action);
            }
        }

        // Loader is the primary category: Fabric mods, Forge mods, NeoForge mods, Quilt mods.
        Map<String, List<ModScanner.ModEntry>> byLoader = new LinkedHashMap<>();
        for (ModScanner.ModEntry mod : mods) {
            if (!byMod.containsKey(mod.id())) {
                continue;
            }
            String loader = "UNKNOWN".equals(mod.loader()) ? instanceLoader : mod.loader();
            byLoader.computeIfAbsent(loader, k -> new ArrayList<>()).add(mod);
        }
        List<String> loaderOrder = new ArrayList<>(List.of("FABRIC", "FORGE", "NEOFORGE", "QUILT"));
        for (String loader : byLoader.keySet()) {
            if (!loaderOrder.contains(loader)) {
                loaderOrder.add(loader);
            }
        }
        for (String loader : loaderOrder) {
            List<ModScanner.ModEntry> loaderMods = byLoader.get(loader);
            if (loaderMods == null || loaderMods.isEmpty()) {
                continue;
            }
            Label heading = Ui.label(loaderLabel(loader) + " 模组", "sidebar-section");
            heading.setPadding(new Insets(10, 0, 0, 2));
            groups.getChildren().add(heading);
            for (ModScanner.ModEntry mod : loaderMods) {
                List<String> actions = byMod.get(mod.id());
                Map<String, String> values = new LinkedHashMap<>();
                for (String action : actions) {
                    values.put(action, binds.getOrDefault(action, ""));
                }
                groups.getChildren().add(groupCard(
                        mod.name() + "  (" + mod.id() + ") · " + loaderLabel(loader), values, mod.id()));
            }
        }
        if (!other.isEmpty()) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String action : other) {
                values.put(action, binds.getOrDefault(action, ""));
            }
            groups.getChildren().add(groupCard("其它 / 未识别的模组", values, null));
        }
        if (byLoader.isEmpty() && other.isEmpty()) {
            groups.getChildren().add(Ui.small(
                    "当前实例没有模组键位记录；安装模组并在游戏里改键后，这里会按加载器分组显示。"));
        }
        long modBinds = binds.size() - Keybinds.ACTION_NAMES.size();
        status.setText(current.id() + " · " + loaderLabel(instanceLoader) + " · " + mods.size()
                + " 个模组 · " + Math.max(0, modBinds) + " 个模组按键 · " + optionsFile);
    }

    private static String loaderLabel(String loader) {
        return switch (loader == null ? "" : loader) {
            case "FABRIC" -> "Fabric";
            case "FORGE" -> "Forge";
            case "NEOFORGE" -> "NeoForge";
            case "QUILT" -> "Quilt";
            case "VANILLA" -> "原版";
            case "OPTIFINE" -> "OptiFine";
            default -> "其它";
        };
    }

    private static ModScanner.ModEntry ownerOf(String action, List<ModScanner.ModEntry> mods) {
        String lowered = action.toLowerCase(Locale.ROOT);
        ModScanner.ModEntry best = null;
        for (ModScanner.ModEntry mod : mods) {
            String id = mod.id().toLowerCase(Locale.ROOT);
            if (id.length() < 2) {
                continue;
            }
            if (lowered.contains(id) && (best == null || id.length() > best.id().length())) {
                best = mod;
            }
        }
        return best;
    }

    private Node groupCard(String title, Map<String, String> values, String modId) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(12, 14, 12, 14));
        card.getChildren().add(Ui.h3(title));
        if (values.isEmpty()) {
            card.getChildren().add(Ui.small("没有按键记录"));
            return card;
        }
        for (var entry : values.entrySet()) {
            card.getChildren().add(bindRow(entry.getKey(), entry.getValue(), modId));
        }
        return card;
    }

    private HBox bindRow(String action, String value, String modId) {
        String labelText = Keybinds.label(action);
        if (modId != null && labelText.startsWith(modId + ".")) {
            labelText = labelText.substring(modId.length() + 1);
        }
        Label name = Ui.label(labelText, "small");
        name.setPrefWidth(220);
        name.setMinWidth(150);

        Label current = Ui.label(Keybinds.display(value), "small", "muted");
        current.getStyleClass().add("chip");
        current.setMinWidth(120);
        current.setAlignment(Pos.CENTER);
        valueLabels.put(action, current);

        Button edit = new Button("修改");
        edit.getStyleClass().addAll("btn", "btn-small");
        edit.setMinWidth(64);
        edit.setOnAction(event -> startCapture(action));
        editButtons.put(action, edit);

        Button clear = new Button("清除");
        clear.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        clear.setOnAction(event -> {
            binds.put(action, "");
            current.setText("未绑定");
        });

        HBox row = new HBox(10, name, current, Ui.spacer(), clear, edit);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 6, 4, 6));
        row.getStyleClass().add("row-hover");
        return row;
    }

    private void startCapture(String action) {
        endCapture();
        capturing = action;
        Button button = editButtons.get(action);
        if (button != null) {
            button.setText("请按键...");
        }
        Label value = valueLabels.get(action);
        if (value != null) {
            value.setText("...");
        }
        afterCapture = () -> {
            if (button != null) {
                button.setText("修改");
            }
        };
    }

    private void finishCapture(String glfwName) {
        if (capturing == null) {
            return;
        }
        binds.put(capturing, glfwName);
        Label value = valueLabels.get(capturing);
        if (value != null) {
            value.setText(Keybinds.display(glfwName));
        }
        endCapture();
    }

    private void endCapture() {
        capturing = null;
        if (afterCapture != null) {
            afterCapture.run();
            afterCapture = null;
        }
    }

    private void installCapture(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (capturing == null) {
                return;
            }
            event.consume();
            if (KeyNames.isCancel(event.getCode())) {
                endCapture();
                return;
            }
            finishCapture(KeyNames.glfw(event.getCode()));
        });
        scene.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            if (capturing != null) {
                event.consume();
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (capturing == null) {
                return;
            }
            event.consume();
            finishCapture(KeyNames.glfw(event.getButton()));
        });
    }

    private void save() {
        if (optionsFile == null) {
            return;
        }
        Keybinds.apply(optionsFile, binds);
        context.settings.save();
        status.setText("已保存到 " + optionsFile);
        host.toast("键位已保存");
    }

    private void applyToOthers() {
        if (binds.isEmpty() || current == null) {
            return;
        }
        javafx.stage.Window owner = groups.getScene() == null ? null : groups.getScene().getWindow();
        dev.yml.ui.component.DialogShell picker = new dev.yml.ui.component.DialogShell(owner,
                "同步键位到其它版本", 540, 540);
        VBox list = new VBox(4);
        List<javafx.scene.control.CheckBox> boxes = new ArrayList<>();
        for (LocalVersions.Installed installed : instances) {
            if (installed.id().equals(current.id())) {
                continue;
            }
            javafx.scene.control.CheckBox box = new javafx.scene.control.CheckBox(
                    installed.id() + "  ·  " + installed.loader().label());
            box.setSelected(true);
            box.setUserData(installed);
            boxes.add(box);
            list.getChildren().add(box);
        }
        if (boxes.isEmpty()) {
            list.getChildren().add(Ui.small("没有其它已安装版本"));
        }
        HBox quick = new HBox(8);
        Button all = new Button("全选");
        all.getStyleClass().addAll("btn", "btn-small");
        all.setOnAction(event -> boxes.forEach(b -> b.setSelected(true)));
        Button none = new Button("全不选");
        none.getStyleClass().addAll("btn", "btn-small");
        none.setOnAction(event -> boxes.forEach(b -> b.setSelected(false)));
        quick.getChildren().addAll(all, none);

        Label hint = Ui.label("只写入 key_* 键位，其它游戏选项保持不变。", "tiny", "faint");
        hint.setWrapText(true);
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        picker.setContent(new VBox(10, hint, quick, scroll));

        Button cancel = new Button("取消");
        cancel.getStyleClass().add("btn");
        cancel.setOnAction(event -> picker.close());
        Button apply = new Button("同步");
        apply.getStyleClass().addAll("btn", "btn-primary");
        apply.setOnAction(event -> {
            int count = 0;
            for (javafx.scene.control.CheckBox box : boxes) {
                if (!box.isSelected()) {
                    continue;
                }
                LocalVersions.Installed target = (LocalVersions.Installed) box.getUserData();
                Keybinds.apply(context.optionsFile(target), binds);
                count++;
            }
            picker.close();
            status.setText("已同步到 " + count + " 个版本");
            host.toast("键位已同步到 " + count + " 个版本");
        });
        picker.setFooter(cancel, apply);
        picker.showAndWait();
    }
}
