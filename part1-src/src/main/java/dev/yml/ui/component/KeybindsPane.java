package dev.yml.ui.component;

import dev.yml.core.config.Keybinds;
import dev.yml.core.game.LocalVersions;
import dev.yml.ui.AppContext;
import dev.yml.ui.KeyNames;
import dev.yml.ui.Ui;
import dev.yml.ui.Util;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reusable key binding editor for one instance's {@code options.txt}, including
 * batch application to other installed versions.
 */
public final class KeybindsPane extends BorderPane {

    private final AppContext context;
    private final LocalVersions.Installed version;
    private final Path optionsFile;
    private final VBox rows = new VBox(2);
    private final Map<String, String> binds = new LinkedHashMap<>();
    private final Map<String, Label> valueLabels = new LinkedHashMap<>();
    private final Map<String, Button> editButtons = new LinkedHashMap<>();

    private String capturing;
    private Runnable afterCapture;
    private Scene boundScene;

    public KeybindsPane(AppContext context, LocalVersions.Installed version) {
        this.context = context;
        this.version = version;
        this.optionsFile = context.optionsFile(version);
        build();
    }

    private void build() {
        binds.putAll(Keybinds.fullList(optionsFile));

        Label path = Ui.label("选项文件: " + optionsFile, "tiny", "faint");
        path.setWrapText(true);
        Label tip = Ui.label("点击“修改”后按下想要的按键，Esc 取消。相同按键请避免重复绑定。", "tiny", "faint");
        tip.setWrapText(true);

        Button applyOthers = new Button("应用到其他版本");
        applyOthers.getStyleClass().addAll("btn", "btn-small");
        applyOthers.setOnAction(event -> showApplyDialog());
        Button reset = new Button("全部清除");
        reset.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        reset.setOnAction(event -> {
            if (!Util.confirm(getScene() == null ? null : getScene().getWindow(), "清除键位",
                    "将所有按键恢复为未绑定？保存后生效。")) {
                return;
            }
            for (String action : new ArrayList<>(binds.keySet())) {
                binds.put(action, "");
                Label value = valueLabels.get(action);
                if (value != null) {
                    value.setText("未绑定");
                }
            }
        });
        HBox toolbar = new HBox(8, tip, Ui.spacer(), reset, applyOthers);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        rows.setPadding(new Insets(4, 0, 8, 0));
        for (var entry : binds.entrySet()) {
            rows.getChildren().add(bindRow(entry.getKey(), entry.getValue()));
        }
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox top = new VBox(5, path, toolbar);
        top.setPadding(new Insets(0, 4, 6, 0));
        setTop(top);
        setCenter(scroll);
        BorderPane.setMargin(scroll, new Insets(6, 4, 0, 0));

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && newScene != boundScene) {
                boundScene = newScene;
                captureOn(newScene);
            }
        });
    }

    private HBox bindRow(String action, String value) {
        Label name = Ui.label(Keybinds.label(action), "small");
        name.setPrefWidth(180);
        name.setMinWidth(140);

        Label current = Ui.label(Keybinds.display(value), "small", "muted");
        current.getStyleClass().add("chip");
        current.setMinWidth(128);
        current.setAlignment(Pos.CENTER);
        valueLabels.put(action, current);

        Button edit = new Button("修改");
        edit.getStyleClass().addAll("btn", "btn-small");
        edit.setMinWidth(66);
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
        row.setPadding(new Insets(5, 8, 5, 8));
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
        String action = capturing;
        binds.put(action, glfwName);
        Label value = valueLabels.get(action);
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

    private void captureOn(Scene scene) {
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

    /** Writes the current bindings to this instance's options.txt. */
    public void save() {
        Keybinds.apply(optionsFile, binds);
    }

    public void applyTo(Path targetOptions) {
        Keybinds.apply(targetOptions, binds);
    }

    public Map<String, String> binds() {
        return binds;
    }

    public Path optionsFile() {
        return optionsFile;
    }

    private void showApplyDialog() {
        DialogShell picker = new DialogShell(getScene().getWindow(), "应用到其他版本", 540, 540);
        VBox list = new VBox(4);
        List<CheckBox> boxes = new ArrayList<>();
        for (LocalVersions.Installed installed : context.versions()) {
            if (installed.id().equals(version.id())) {
                continue;
            }
            CheckBox box = new CheckBox(installed.id());
            box.setSelected(true);
            box.setUserData(installed);
            boxes.add(box);
            list.getChildren().add(box);
        }
        if (boxes.isEmpty()) {
            list.getChildren().add(Ui.small("没有其他已安装的版本"));
        }
        HBox quick = new HBox(8);
        Button all = new Button("全选");
        all.getStyleClass().addAll("btn", "btn-small");
        all.setOnAction(event -> boxes.forEach(b -> b.setSelected(true)));
        Button none = new Button("全不选");
        none.getStyleClass().addAll("btn", "btn-small");
        none.setOnAction(event -> boxes.forEach(b -> b.setSelected(false)));
        quick.getChildren().addAll(all, none);

        Label hint = Ui.label("仅写入按键设置（key_*），其他游戏选项保持不变。", "tiny", "faint");
        hint.setWrapText(true);
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        picker.setContent(new VBox(10, hint, quick, scroll));

        Button cancel = new Button("取消");
        cancel.getStyleClass().add("btn");
        cancel.setOnAction(event -> picker.close());
        Button apply = new Button("应用");
        apply.getStyleClass().addAll("btn", "btn-primary");
        apply.setOnAction(event -> {
            int count = 0;
            for (CheckBox box : boxes) {
                if (!box.isSelected()) {
                    continue;
                }
                LocalVersions.Installed target = (LocalVersions.Installed) box.getUserData();
                Keybinds.apply(context.optionsFile(target), binds);
                count++;
            }
            picker.close();
            Util.info(getScene().getWindow(), "键位已应用", "已写入 " + count + " 个版本的按键设置");
        });
        picker.setFooter(cancel, apply);
        picker.showAndWait();
    }
}
