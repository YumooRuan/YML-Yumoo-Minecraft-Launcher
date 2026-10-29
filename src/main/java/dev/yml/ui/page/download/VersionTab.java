package dev.yml.ui.page.download;

import dev.yml.core.game.GameLauncher;
import dev.yml.core.game.VersionManifest;
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
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Remote vanilla version browser and installer. */
public final class VersionTab implements DownloadTab {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane root = new BorderPane();
    private final ListView<VersionManifest.Remote> list = new ListView<>();
    private final TextField search = new TextField();
    private final Label status = Ui.label("正在获取版本列表...", "tiny", "faint");
    private final HBox filters = new HBox(6);
    private final Map<String, Button> filterButtons = new LinkedHashMap<>();

    private List<VersionManifest.Remote> all = new ArrayList<>();
    private String filterType = "release";
    private String query = "";
    private boolean loading;
    private boolean loaded;

    public VersionTab(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        root.setPadding(new Insets(0));

        filterButtons.put("release", filterButton("正式版", "release"));
        filterButtons.put("snapshot", filterButton("快照", "snapshot"));
        filterButtons.put("old", filterButton("远古版本", "old"));
        filterButtons.put("all", filterButton("全部", "all"));
        filters.getChildren().addAll(filterButtons.values());

        search.setPromptText("搜索版本号");
        search.setPrefWidth(220);
        search.textProperty().addListener((obs, old, value) -> {
            query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            applyFilter();
        });
        Button reload = new Button();
        reload.setGraphic(Icon.of(Icon.REFRESH, 14, Ui.TEXT_DIM));
        reload.getStyleClass().addAll("btn", "btn-small");
        reload.setOnAction(event -> load(true));
        Ui.tooltip(reload, "刷新版本列表");

        HBox toolbar = new HBox(8, filters, Ui.spacer(), search, reload);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 10, 0));

        list.setCellFactory(view -> new VersionCell());
        list.setPlaceholder(Ui.small("没有匹配的版本"));
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setFocusTraversable(false);

        VBox top = new VBox(toolbar);
        root.setTop(top);
        root.setCenter(list);
        root.setBottom(status);
        BorderPane.setMargin(status, new Insets(8, 0, 0, 2));
        setFilterActive("release");
    }

    private Button filterButton(String text, String id) {
        Button button = new Button(text);
        button.getStyleClass().addAll("tab");
        button.setOnAction(event -> {
            filterType = id;
            setFilterActive(id);
            applyFilter();
        });
        return button;
    }

    private void setFilterActive(String id) {
        for (var entry : filterButtons.entrySet()) {
            entry.getValue().getStyleClass().remove("tab-active");
            if (entry.getKey().equals(id)) {
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
        if (!loaded && !loading) {
            load(false);
        }
    }

    @Override
    public void refresh() {
        load(true);
    }

    private void load(boolean force) {
        if (loading) {
            return;
        }
        loading = true;
        status.setText("正在获取版本列表...");
        context.io.submit(() -> {
            try {
                VersionManifest.Data data = VersionManifest.load(context.source(),
                        context.dataRoot.resolve("cache").resolve("version_manifest_v2.json"), force);
                Platform.runLater(() -> {
                    all = data.versions;
                    loading = false;
                    loaded = true;
                    applyFilter();
                    status.setText("共 " + all.size() + " 个版本"
                            + (data.fromCache ? " · 来自本地缓存" : "")
                            + (data.latestRelease == null ? "" : " · 最新正式版 " + data.latestRelease));
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    loading = false;
                    status.setText("获取版本列表失败：" + e.getMessage());
                });
            }
        });
    }

    private void applyFilter() {
        List<VersionManifest.Remote> filtered = new ArrayList<>();
        for (VersionManifest.Remote remote : all) {
            if (!matchesType(remote.type())) {
                continue;
            }
            if (!query.isEmpty() && !remote.id().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            filtered.add(remote);
        }
        list.getItems().setAll(filtered);
    }

    private boolean matchesType(String type) {
        return switch (filterType) {
            case "all" -> true;
            case "old" -> "old_beta".equals(type) || "old_alpha".equals(type);
            case "snapshot" -> "snapshot".equals(type);
            default -> "release".equals(type);
        };
    }

    private String typeLabel(String type) {
        return switch (type == null ? "release" : type) {
            case "snapshot" -> "快照";
            case "old_beta" -> "远古 Beta";
            case "old_alpha" -> "远古 Alpha";
            default -> "正式版";
        };
    }

    private boolean installed(String id) {
        return Files.isDirectory(context.settings.mcPath().resolve("versions").resolve(id));
    }

    private final class VersionCell extends ListCell<VersionManifest.Remote> {

        @Override
        protected void updateItem(VersionManifest.Remote item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label id = Ui.label(item.id(), "h3");
            Label typeChip = Ui.chip(typeLabel(item.type()),
                    "snapshot".equals(item.type()) ? "chip-orange" : "chip-green");
            Label date = Ui.label(item.releaseTime().length() >= 10 ? item.releaseTime().substring(0, 10) : "",
                    "tiny", "faint");
            HBox meta = new HBox(8, typeChip, date);
            meta.setAlignment(Pos.CENTER_LEFT);
            VBox info = new VBox(4, id, meta);

            Button install = new Button(installed(item.id()) ? "重新安装" : "安装");
            install.getStyleClass().addAll("btn", "btn-small", installed(item.id()) ? "btn-ghost" : "btn-primary");
            install.setOnAction(event -> install(item, install));

            HBox row = new HBox(12, info, Ui.spacer(), install);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 14, 10, 14));
            setGraphic(row);
            setText(null);
            setPadding(Insets.EMPTY);
        }
    }

    private void install(VersionManifest.Remote remote, Button button) {
        Path dir = context.settings.mcPath().resolve("versions").resolve(remote.id());
        if (Files.isDirectory(dir)) {
            boolean ok = Util.confirm(host.window(), "版本已存在",
                    remote.id() + " 已安装，是否重新下载缺失文件？");
            if (!ok) {
                return;
            }
        }
        button.setDisable(true);
        button.setText("安装中");
        host.runTask("安装 " + remote.id(), progress -> {
            GameLauncher.installVanilla(remote.id(), remote.url(), context.settings.mcPath(),
                    context.source(), context.settings.threads, progress);
        }, () -> {
            context.refreshVersions();
            host.refreshAll();
            host.toast(remote.id() + " 安装完成，可在下载页右上角「资源包」继续下载");
            list.refresh();
        });
    }
}
