package dev.yml.ui.page.download;

import dev.yml.core.content.CurseForgeClient;
import dev.yml.core.content.LocalModpackInstaller;
import dev.yml.core.content.ModrinthClient;
import dev.yml.core.content.MrPackInstaller;
import dev.yml.core.content.WorldInstaller;
import dev.yml.core.game.LocalVersions;
import dev.yml.core.java.JavaRuntime;
import dev.yml.core.net.Downloader;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Progress;
import dev.yml.ui.AppContext;
import dev.yml.ui.Ui;
import dev.yml.ui.UiHost;
import dev.yml.ui.Util;
import dev.yml.ui.component.Icon;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Browses and installs mods, shaders, resource packs, modpacks and maps. */
public final class ContentTab implements DownloadTab {

    private static final Map<String, String> TYPES = new LinkedHashMap<>();
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        TYPES.put("mod", "模组");
        TYPES.put("shader", "光影");
        TYPES.put("resourcepack", "资源包");
        TYPES.put("modpack", "整合包");
        TYPES.put("world", "地图");
        for (var entry : TYPES.entrySet()) {
            LABELS.put(entry.getValue(), entry.getKey());
        }
    }

    private final AppContext context;
    private final UiHost host;
    private final BorderPane root = new BorderPane();
    private final ComboBox<String> typeBox = new ComboBox<>();
    private final ComboBox<LocalVersions.Installed> instanceBox = new ComboBox<>();
    private final TextField searchField = new TextField();
    private final ListView<Object> results = new ListView<>();
    private final Label status = Ui.label("", "tiny", "faint");
    private final Label hint = Ui.label("", "tiny", "faint");

    private final List<LocalVersions.Installed> instances = new ArrayList<>();
    private String type = "mod";
    private boolean loading;
    private boolean initialized;
    private int searchVersion;
    private boolean updatingInstances;
    private boolean preparingType;
    private List<?> currentResults = List.of();

    public ContentTab(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        typeBox.getItems().setAll(LABELS.keySet());
        typeBox.getSelectionModel().select("模组");
        typeBox.setOnAction(event -> {
            type = LABELS.getOrDefault(typeBox.getSelectionModel().getSelectedItem(), "mod");
            refreshHint();
            if (!preparingType) {
                search();
            }
        });

        instanceBox.setPrefWidth(270);
        instanceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalVersions.Installed value) {
                return value == null ? "未选择实例" : value.id() + " · " + value.loader().label();
            }

            @Override
            public LocalVersions.Installed fromString(String string) {
                return null;
            }
        });
        instanceBox.setOnAction(event -> {
            refreshHint();
            if (!updatingInstances) {
                search();
            }
        });

        searchField.setPromptText("搜索名称或关键词，例如 sodium / iris / adventure");
        searchField.setOnAction(event -> search());
        Button searchButton = new Button("搜索");
        searchButton.getStyleClass().addAll("btn", "btn-primary", "btn-small");
        searchButton.setOnAction(event -> search());

        HBox toolbar = new HBox(8, typeBox, instanceBox, searchField, searchButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setMaxWidth(Double.MAX_VALUE);

        Button localPack = new Button("安装整合包文件");
        localPack.getStyleClass().addAll("btn", "btn-small");
        localPack.setOnAction(event -> installLocalModpack());
        Button localMap = new Button("导入本地地图");
        localMap.getStyleClass().addAll("btn", "btn-small");
        localMap.setOnAction(event -> installLocalMap());

        HBox topBar = new HBox(10, toolbar, localPack, localMap);
        topBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(toolbar, Priority.ALWAYS);

        results.setCellFactory(view -> new ResultCell());
        results.setPlaceholder(Ui.small("输入关键词开始搜索"));
        results.setFocusTraversable(false);

        hint.setWrapText(true);
        status.setPadding(new Insets(6, 0, 0, 2));
        VBox top = new VBox(8, topBar, hint);
        root.setTop(top);
        root.setCenter(results);
        root.setBottom(status);
        BorderPane.setMargin(results, new Insets(10, 0, 0, 0));
    }

    private LocalVersions.Installed instance() {
        return instanceBox.getSelectionModel().getSelectedItem();
    }

    private String gameVersion() {
        LocalVersions.Installed installed = instance();
        if (installed == null || installed.info() == null) {
            return null;
        }
        return installed.info().baseVersion();
    }

    private String loaderFacet() {
        LocalVersions.Installed installed = instance();
        if (installed == null) {
            return null;
        }
        return switch (installed.loader()) {
            case FABRIC -> "fabric";
            case FORGE -> "forge";
            case NEOFORGE -> "neoforge";
            case QUILT -> "quilt";
            default -> null;
        };
    }

    private void refreshHint() {
        LocalVersions.Installed installed = instance();
        if ("world".equals(type)) {
            hint.setText("地图来自 CurseForge 镜像，无需 Key；自动下载最新文件并解压到 saves");
        } else if ("shader".equals(type)) {
            hint.setText("光影安装到实例的 shaderpacks 目录，需要 Iris 或 OptiFine 支持");
        } else if ("resourcepack".equals(type)) {
            hint.setText("资源包安装到实例的 resourcepacks 目录");
        } else if ("modpack".equals(type)) {
            hint.setText("整合包会安装为新的独立实例（自动安装所需加载器）");
        } else {
            hint.setText("");
        }
    }

    private String modsDirName() {
        LocalVersions.Installed installed = instance();
        if (installed == null) {
            return "mods";
        }
        var settings = context.instanceSettings(installed);
        Path dir = settings.isolated ? installed.dir() : context.settings.mcPath();
        return dir.resolve("mods").toString();
    }

    @Override
    public Node view() {
        return root;
    }

    @Override
    public void onShow() {
        refreshInstances();
        refreshHint();
        search();
    }

    @Override
    public void refresh() {
        refreshInstances();
        search();
    }

    /** Used by the download page quick entries (for example resource packs). */
    public void setType(String id) {
        prepareType(id);
        search();
    }

    /** Selects a type without searching; the next onShow performs a single search. */
    public void prepareType(String id) {
        for (var entry : LABELS.entrySet()) {
            if (entry.getValue().equals(id)) {
                preparingType = true;
                try {
                    type = id;
                    typeBox.getSelectionModel().select(entry.getKey());
                } finally {
                    preparingType = false;
                }
                refreshHint();
                return;
            }
        }
    }

    private String targetDirectory(LocalVersions.Installed installed) {
        var settings = context.instanceSettings(installed);
        Path dir = settings.isolated ? installed.dir() : context.settings.mcPath();
        String folder = switch (type) {
            case "resourcepack" -> "resourcepacks";
            case "shader" -> "shaderpacks";
            case "world" -> "saves";
            default -> "mods";
        };
        return dir.resolve(folder).toString();
    }

    private void installLocalModpack() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("选择整合包");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                "整合包 (*.mrpack, *.zip)", "*.mrpack", "*.zip"));
        java.io.File file = chooser.showOpenDialog(host.window());
        if (file == null) {
            return;
        }
        java.nio.file.Path archive = file.toPath();
        host.runTask("导入整合包 " + file.getName(), progress -> {
            try {
                String id;
                if (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".mrpack")) {
                    MrPackInstaller.Result result = MrPackInstaller.install(archive, context.settings.mcPath(),
                            resolveJava(), context.dataRoot.resolve("cache"), context.source(),
                            context.settings.threads, progress);
                    id = result.versionId();
                } else {
                    id = LocalModpackInstaller.install(archive, context.settings.mcPath(), resolveJava(),
                            context.dataRoot.resolve("cache"), context.source(),
                            context.settings.threads, progress);
                }
                progress.note("已创建实例 " + id);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }, () -> {
            context.refreshVersions();
            host.refreshAll();
            refreshInstances();
            host.toast("整合包导入完成");
        });
    }

    private void installLocalMap() {
        LocalVersions.Installed installed = instance();
        if (installed == null) {
            Util.error(host.window(), "未选择实例", "请先选择一个实例，地图会安装到它的 saves 目录。");
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("选择地图压缩包");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                "地图压缩包 (*.zip)", "*.zip"));
        java.io.File file = chooser.showOpenDialog(host.window());
        if (file == null) {
            return;
        }
        Path archive = file.toPath();
        String name = file.getName().toLowerCase(Locale.ROOT).endsWith(".zip")
                ? file.getName().substring(0, file.getName().length() - 4) : file.getName();
        host.runTask("导入地图 " + file.getName(), progress -> {
            try {
                var settings = context.instanceSettings(installed);
                Path gameDir = settings.isolated ? installed.dir() : context.settings.mcPath();
                Path save = WorldInstaller.install(archive, gameDir, name);
                progress.note("已导入存档 " + save.getFileName());
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }, () -> {
            host.toast("地图已导入到 saves");
        });
    }

    private void refreshInstances() {
        updatingInstances = true;
        try {
            instances.clear();
            instances.addAll(context.versions());
            instanceBox.getItems().setAll(instances);
            LocalVersions.Installed selected = context.selected();
            int index = -1;
            if (selected != null) {
                for (int i = 0; i < instances.size(); i++) {
                    if (instances.get(i).id().equals(selected.id())) {
                        index = i;
                        break;
                    }
                }
            }
            if (index < 0 && !instances.isEmpty()) {
                index = 0;
            }
            if (index >= 0) {
                instanceBox.getSelectionModel().select(index);
                if (selected == null) {
                    context.select(instances.get(index));
                }
            }
        } finally {
            updatingInstances = false;
        }
        refreshHint();
    }

    private void search() {
        int request = ++searchVersion;
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        String kind = type;
        String gameVersion = gameVersion();
        String loader = "mod".equals(kind) || "modpack".equals(kind) ? loaderFacet() : null;
        loading = true;
        status.setText("正在搜索...");
        results.getItems().clear();
        results.setPlaceholder(Ui.small(query.isEmpty() ? "正在加载推荐内容..." : "正在搜索..."));
        context.io.submit(() -> {
            List<?> found = "world".equals(kind)
                    ? CurseForgeClient.searchWorlds(context.settings.curseForgeApiKey, query, 30)
                    : ModrinthClient.search(query, kind, gameVersion, loader, 30);
            Platform.runLater(() -> {
                if (request != searchVersion) {
                    return;
                }
                loading = false;
                currentResults = found;
                results.getItems().setAll(found);
                results.setPlaceholder(Ui.small("没有找到结果，换个关键词试试"));
                status.setText("找到 " + found.size() + " 个结果");
            });
        });
    }

    private Node mapKeyNotice() {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        Label title = Ui.label("地图下载需要 CurseForge API Key", "small");
        Label desc = Ui.small("在设置中填入免费 Key 后即可搜索地图；也可以直接用「导入本地地图」安装 zip。");
        desc.setWrapText(true);
        Button openSettings = new Button("打开设置填写 Key");
        openSettings.getStyleClass().addAll("btn", "btn-small");
        openSettings.setOnAction(event -> host.showPage("settings"));
        Button applyKey = new Button("申请免费 Key");
        applyKey.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        applyKey.setOnAction(event -> Util.openUrl("https://console.curseforge.com/"));
        HBox buttons = new HBox(8, openSettings, applyKey);
        buttons.setAlignment(Pos.CENTER);
        box.getChildren().addAll(title, desc, buttons);
        return box;
    }

    // ------------------------------------------------------------- install

    private void install(ModrinthClient.Hit hit) {
        LocalVersions.Installed installed = instance();
        if (installed == null && !"modpack".equals(type)) {
            Util.error(host.window(), "未选择实例", "请先在下载页或主页选择一个版本实例。");
            return;
        }
        if (installed != null && !"modpack".equals(type) && !"world".equals(type)) {
            boolean installHere = Util.confirm(host.window(), "安装到当前实例",
                    "目标实例：" + installed.id() + "\n"
                            + "目录：" + targetDirectory(installed) + "\n\n"
                            + "要安装到当前实例吗？",
                    "安装到当前实例", "取消");
            if (!installHere) {
                return;
            }
        }
        String kind = type;
        String loader = "mod".equals(kind) || "modpack".equals(kind) ? loaderFacet() : null;
        String gameVersion = gameVersion();
        host.runTask("安装 " + hit.title(), progress -> {
            progress.note("获取版本信息");
            List<ModrinthClient.Version> versions = ModrinthClient.versions(hit.projectId(), gameVersion, loader);
            if (versions.isEmpty()) {
                throw new RuntimeException("没有适配 " + gameVersion + " 的版本");
            }
            ModrinthClient.Version version = versions.get(0);
            ModrinthClient.FileEntry file = ModrinthClient.primaryFile(version);
            if (file == null) {
                throw new RuntimeException("该版本没有可下载文件");
            }
            if ("modpack".equals(kind)) {
                Path cache = context.dataRoot.resolve("cache").resolve("modpacks");
                FileUtil.ensureDir(cache);
                Path archive = cache.resolve(file.filename());
                new Downloader(4).downloadAll(List.of(Downloader.Item.of(file.filename(), file.url(), archive,
                        file.sha1(), file.size())), progress);
                try {
                    MrPackInstaller.Result result = MrPackInstaller.install(archive, context.settings.mcPath(),
                            resolveJava(), context.dataRoot.resolve("cache"), context.source(),
                            context.settings.threads, progress);
                    progress.note("已创建实例 " + result.versionId());
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            } else {
                Path dir = installDirectory(installed, kind);
                FileUtil.ensureDir(dir);
                new Downloader(4).downloadAll(List.of(Downloader.Item.of(file.filename(), file.url(),
                        dir.resolve(file.filename()), file.sha1(), file.size())), progress);
                progress.note("已安装到 " + dir);
            }
        }, () -> {
            context.refreshVersions();
            host.refreshAll();
            refreshInstances();
            host.toast(hit.title() + " 安装完成");
        });
    }

    private Path installDirectory(LocalVersions.Installed installed, String kind) {
        var settings = context.instanceSettings(installed);
        Path gameDir = settings.isolated ? installed.dir() : context.settings.mcPath();
        return switch (kind) {
            case "shader" -> gameDir.resolve("shaderpacks");
            case "resourcepack" -> gameDir.resolve("resourcepacks");
            default -> gameDir.resolve("mods");
        };
    }

    private Path resolveJava() {
        JavaRuntime runtime = context.javaManager.best(17);
        return runtime == null ? null : runtime.executable;
    }

    private void installMap(CurseForgeClient.Hit hit) {
        LocalVersions.Installed installed = instance();
        if (installed == null) {
            Util.error(host.window(), "未选择实例", "请先选择一个实例，地图会安装到它的存档目录。");
            return;
        }
        host.runTask("下载地图 " + hit.name(), progress -> {
            progress.note("获取地图文件");
            CurseForgeClient.CfFile file = CurseForgeClient.latestWorldFile(context.settings.curseForgeApiKey, hit.id());
            if (file == null) {
                throw new RuntimeException("没有可下载的地图文件");
            }
            Path cache = context.dataRoot.resolve("cache").resolve("worlds");
            FileUtil.ensureDir(cache);
            Path archive = cache.resolve(file.fileName());
            new Downloader(4).downloadAll(List.of(Downloader.Item.of(file.fileName(), file.downloadUrl(),
                    archive, null, file.size())), progress);
            var settings = context.instanceSettings(installed);
            Path gameDir = settings.isolated ? installed.dir() : context.settings.mcPath();
            Path save;
            try {
                save = WorldInstaller.install(archive, gameDir, hit.name());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
            progress.note("已安装到 " + save);
        }, () -> host.toast(hit.name() + " 安装完成"));
    }

    // ---------------------------------------------------------------- cell

    private final class ResultCell extends ListCell<Object> {

        @Override
        protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            String title;
            String description;
            long downloads;
            String subtitle;
            Runnable action;
            if (item instanceof ModrinthClient.Hit hit) {
                title = hit.title();
                description = hit.description();
                downloads = hit.downloads();
                subtitle = hit.author();
                action = () -> install(hit);
            } else {
                CurseForgeClient.Hit hit = (CurseForgeClient.Hit) item;
                title = hit.name();
                description = hit.summary();
                downloads = hit.downloads();
                subtitle = "CurseForge 地图";
                action = () -> installMap(hit);
            }
            String iconUrl;
            if (item instanceof ModrinthClient.Hit hit) {
                iconUrl = hit.iconUrl();
            } else {
                iconUrl = ((CurseForgeClient.Hit) item).iconUrl();
            }
            Label name = Ui.label(title, "h3");
            Label desc = Ui.label(description, "small", "muted");
            desc.setWrapText(true);
            desc.setMaxWidth(520);
            HBox meta = new HBox(8, Ui.chip(subtitle == null ? "" : subtitle),
                    Ui.chip(Ui.humanCount((int) Math.min(downloads, Integer.MAX_VALUE)) + " 次下载"));
            meta.setAlignment(Pos.CENTER_LEFT);
            VBox box = new VBox(4, name, desc, meta);

            Button install = new Button("安装");
            install.getStyleClass().addAll("btn", "btn-primary", "btn-small");
            install.setOnAction(event -> action.run());
            HBox row = new HBox(12, contentIcon(iconUrl, title), box, Ui.spacer(), install);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 14, 10, 14));
            setGraphic(row);
            setText(null);
            setPadding(Insets.EMPTY);
        }
    }

    private static final Map<String, Image> ICON_CACHE = new HashMap<>();

    private Node contentIcon(String url, String title) {
        StackPane holder = new StackPane();
        holder.setMinSize(56, 56);
        holder.setPrefSize(56, 56);
        holder.setMaxSize(56, 56);
        holder.getStyleClass().add("card-soft");
        Rectangle clip = new Rectangle(56, 56);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        holder.setClip(clip);
        String initial = title == null || title.isBlank() ? "?" : title.substring(0, 1).toUpperCase(Locale.ROOT);
        holder.getChildren().add(Ui.label(initial, "h2", "muted"));
        if (url != null && !url.isBlank()) {
            try {
                Image image = ICON_CACHE.computeIfAbsent(url,
                        key -> new Image(key, 128, 128, true, true, true));
                ImageView view = new ImageView(image);
                view.setFitWidth(56);
                view.setFitHeight(56);
                view.setPreserveRatio(true);
                view.setSmooth(true);
                holder.getChildren().add(view);
            } catch (Exception ignored) {
            }
        }
        return holder;
    }

    public static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    /** Unused placeholder to keep the class list tidy. */
    private static boolean exists(Path path) {
        return Files.exists(path);
    }
}
