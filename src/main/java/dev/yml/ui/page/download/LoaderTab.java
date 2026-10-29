package dev.yml.ui.page.download;

import dev.yml.core.game.LocalVersions;
import dev.yml.core.java.JavaRuntime;
import dev.yml.core.loader.FabricInstaller;
import dev.yml.core.loader.ForgeInstaller;
import dev.yml.core.loader.NeoForgeInstaller;
import dev.yml.core.loader.OptiFineInstaller;
import dev.yml.ui.AppContext;
import dev.yml.ui.Ui;
import dev.yml.ui.UiHost;
import dev.yml.ui.Util;
import dev.yml.ui.component.Icon;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Installs Fabric / Forge / NeoForge / OptiFine on top of an existing vanilla version. */
public final class LoaderTab implements DownloadTab {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane root = new BorderPane();

    private final ComboBox<LocalVersions.Installed> baseBox = new ComboBox<>();
    private final ComboBox<Object> versionBox = new ComboBox<>();
    private final HBox typeBar = new HBox(6);
    private final Map<String, Button> typeButtons = new LinkedHashMap<>();
    private final Label status = Ui.label("", "tiny", "faint");
    private final Button install = new Button("安装到新实例");

    private String type = "fabric";
    private final List<LocalVersions.Installed> bases = new ArrayList<>();
    private List<?> versions = List.of();
    private boolean loading;
    private boolean initialized;

    public LoaderTab(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        for (String[] definition : new String[][]{
                {"fabric", "Fabric"}, {"forge", "Forge"}, {"neoforge", "NeoForge"}, {"optifine", "OptiFine"}}) {
            Button button = new Button(definition[1]);
            button.getStyleClass().add("tab");
            button.setOnAction(event -> {
                type = definition[0];
                updateTypeButtons();
                loadVersions();
            });
            typeButtons.put(definition[0], button);
            typeBar.getChildren().add(button);
        }
        updateTypeButtons();

        baseBox.setPrefWidth(260);
        baseBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalVersions.Installed value) {
                return value == null ? "" : value.id() + " · " + value.loader().label();
            }

            @Override
            public LocalVersions.Installed fromString(String string) {
                return null;
            }
        });
        baseBox.setOnAction(event -> loadVersions());

        versionBox.setPrefWidth(300);
        versionBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Object value) {
                if (value == null) {
                    return "";
                }
                if (value instanceof FabricInstaller.LoaderVersion fabric) {
                    return fabric.toString();
                }
                if (value instanceof ForgeInstaller.ForgeVersion forge) {
                    return forge.forgeVersion();
                }
                if (value instanceof NeoForgeInstaller.NeoVersion neo) {
                    return neo.version();
                }
                if (value instanceof OptiFineInstaller.OptiFineVersion optiFine) {
                    return optiFine.label();
                }
                return value.toString();
            }

            @Override
            public Object fromString(String string) {
                return null;
            }
        });
        install.getStyleClass().addAll("btn", "btn-primary");
        install.setOnAction(event -> install());

        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(18, 20, 18, 20));
        card.getChildren().addAll(
                Ui.h3("选择基础版本与加载器"),
                Ui.label("加载器会创建一个新的版本实例，原版文件保持不变。", "tiny", "faint"),
                row("基础版本", baseBox),
                row("加载器", typeBar),
                row("加载器版本", new HBox(10, versionBox, install)),
                status);

        Label tip = Ui.label("提示：Forge / NeoForge 安装器会联网下载依赖，首次安装可能需要几分钟。", "tiny", "faint");
        tip.setWrapText(true);

        VBox content = new VBox(14, card, tip);
        content.setPadding(new Insets(14, 0, 0, 0));
        root.setCenter(content);
    }

    private HBox row(String label, Node control) {
        Label name = Ui.label(label, "small", "muted");
        name.setMinWidth(88);
        HBox box = new HBox(12, name, control);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        return box;
    }

    private void updateTypeButtons() {
        for (var entry : typeButtons.entrySet()) {
            entry.getValue().getStyleClass().remove("tab-active");
            if (entry.getKey().equals(type)) {
                entry.getValue().getStyleClass().add("tab-active");
            }
        }
    }

    @Override
    public Node view() {
        return root;
    }

    @Override
    public void onShow() {
        refreshBases();
        if (!initialized) {
            initialized = true;
            loadVersions();
        }
    }

    @Override
    public void refresh() {
        refreshBases();
        loadVersions();
    }

    private void refreshBases() {
        bases.clear();
        bases.addAll(context.versions());
        baseBox.setItems(FXCollections.observableArrayList(bases));
        if (bases.isEmpty()) {
            status.setText("还没有已安装的版本，请先在“游戏版本”页安装原版");
            install.setDisable(true);
            return;
        }
        install.setDisable(false);
        LocalVersions.Installed selected = context.selected();
        int index = 0;
        if (selected != null) {
            for (int i = 0; i < bases.size(); i++) {
                if (bases.get(i).id().equals(selected.id())) {
                    index = i;
                    break;
                }
            }
        }
        baseBox.getSelectionModel().select(index);
    }

    private LocalVersions.Installed base() {
        return baseBox.getSelectionModel().getSelectedItem();
    }

    private void loadVersions() {
        LocalVersions.Installed base = base();
        if (base == null) {
            return;
        }
        String mc = base.info() == null ? base.id() : base.info().baseVersion();
        versionBox.getItems().clear();
        status.setText("正在获取 " + loaderName() + " 版本...");
        loading = true;
        install.setDisable(true);
        context.io.submit(() -> {
            List<?> list = List.of();
            try {
                list = switch (type) {
                    case "fabric" -> FabricInstaller.loaders(mc, context.source());
                    case "forge" -> ForgeInstaller.versions(mc, context.source());
                    case "neoforge" -> NeoForgeInstaller.versions(mc, context.source());
                    default -> OptiFineInstaller.versions(mc, context.source());
                };
            } catch (Exception e) {
                dev.yml.core.util.Log.warn("加载器版本获取失败: %s", e.getMessage());
            }
            List<?> finalList = list;
            Platform.runLater(() -> {
                versions = finalList;
                loading = false;
                versionBox.getItems().setAll(finalList);
                if (!finalList.isEmpty()) {
                    versionBox.getSelectionModel().select(finalList.size() - 1);
                    status.setText("Minecraft " + mc + " · 找到 " + finalList.size() + " 个 "
                            + loaderName() + " 版本");
                    install.setDisable(false);
                } else {
                    status.setText("Minecraft " + mc + " 没有可用的 " + loaderName() + " 版本");
                    install.setDisable(true);
                }
            });
        });
    }

    private String loaderName() {
        return switch (type) {
            case "fabric" -> "Fabric";
            case "forge" -> "Forge";
            case "neoforge" -> "NeoForge";
            default -> "OptiFine";
        };
    }

    private void install() {
        LocalVersions.Installed base = base();
        Object version = versionBox.getSelectionModel().getSelectedItem();
        if (base == null || version == null || loading) {
            return;
        }
        String mc = base.info() == null ? base.id() : base.info().baseVersion();
        Path mcRoot = context.settings.mcPath();
        Path cache = context.dataRoot.resolve("cache");
        JavaRuntime java = resolveJava();

        switch (type) {
            case "fabric" -> {
                FabricInstaller.LoaderVersion loader = (FabricInstaller.LoaderVersion) version;
                run("安装 Fabric " + loader.version(), progress -> {
                    String id = FabricInstaller.install(mc, loader.version(), mcRoot, context.source(),
                            context.settings.threads, progress);
                    progress.note("已创建实例 " + id);
                });
            }
            case "forge" -> {
                if (java == null) {
                    noJava();
                    return;
                }
                ForgeInstaller.ForgeVersion forge = (ForgeInstaller.ForgeVersion) version;
                run("安装 Forge " + forge.forgeVersion(), progress -> {
                    String id = ForgeInstaller.install(forge, mcRoot, java.executable, cache,
                            context.source(), progress, null);
                    progress.note("已创建实例 " + id);
                });
            }
            case "neoforge" -> {
                if (java == null) {
                    noJava();
                    return;
                }
                NeoForgeInstaller.NeoVersion neo = (NeoForgeInstaller.NeoVersion) version;
                run("安装 NeoForge " + neo.version(), progress -> {
                    String id = NeoForgeInstaller.install(neo, mcRoot, java.executable, cache,
                            context.source(), progress, null);
                    progress.note("已创建实例 " + id);
                });
            }
            default -> {
                OptiFineInstaller.OptiFineVersion optiFine = (OptiFineInstaller.OptiFineVersion) version;
                Path javaExe = java != null ? java.executable
                        : Path.of(System.getProperty("java.home"), "bin", "java.exe");
                run("安装 OptiFine " + optiFine.label(), progress -> {
                    String id = OptiFineInstaller.install(optiFine, mcRoot, javaExe, cache, progress, null);
                    progress.note("已创建实例 " + id);
                });
            }
        }
    }

    private void noJava() {
        Util.error(host.window(), "缺少 Java", "安装加载器需要一个可用的 Java，请先在下载页安装 Java 17 或更高版本。");
    }

    private JavaRuntime resolveJava() {
        if (context.settings.defaultJavaPath != null && !context.settings.defaultJavaPath.isBlank()) {
            JavaRuntime explicit = JavaRuntime.probe(Path.of(context.settings.defaultJavaPath), "默认设置");
            if (explicit != null) {
                return explicit;
            }
        }
        return context.javaManager.best(17);
    }

    private void run(String title, java.util.function.Consumer<dev.yml.core.util.Progress> task) {
        host.runTask(title, task, () -> {
            context.refreshVersions();
            host.refreshAll();
            refreshBases();
            host.toast(title + " 完成");
        });
    }
}
