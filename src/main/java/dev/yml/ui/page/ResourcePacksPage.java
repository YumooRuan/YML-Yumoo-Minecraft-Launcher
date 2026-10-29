package dev.yml.ui.page;

import dev.yml.core.config.InstanceSettings;
import dev.yml.core.game.LocalVersions;
import dev.yml.ui.AppContext;
import dev.yml.ui.Page;
import dev.yml.ui.Ui;
import dev.yml.ui.UiHost;
import dev.yml.ui.Util;
import dev.yml.ui.component.Icon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local resource pack management with quick enable/disable. */
public final class ResourcePacksPage implements Page {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane view = new BorderPane();
    private final ComboBox<String> instanceBox = new ComboBox<>();
    private final VBox list = new VBox(4);
    private final Label pathLabel = Ui.label("", "tiny", "faint");
    private final Label summary = Ui.label("", "tiny", "faint");
    private final List<LocalVersions.Installed> instances = new ArrayList<>();
    private LocalVersions.Installed current;

    public ResourcePacksPage(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        view.setPadding(new Insets(18, 20, 14, 20));
        HBox header = Ui.row(10, Ui.h1("资源包"), Ui.small("启用 / 禁用通过重命名实现，可直接添加上传 zip"));
        header.setAlignment(Pos.CENTER_LEFT);

        instanceBox.setPrefWidth(300);
        instanceBox.setOnAction(event -> selectInstance());
        Button open = new Button("打开资源包文件夹");
        open.getStyleClass().addAll("btn", "btn-small");
        open.setOnAction(event -> Util.openFolder(resourceDir()));
        Button add = new Button("添加资源包");
        add.getStyleClass().addAll("btn", "btn-small");
        add.setOnAction(event -> addPacks());
        Button download = new Button("下载资源包");
        download.getStyleClass().addAll("btn", "btn-small");
        download.setOnAction(event -> {
            host.showPage("download");
            host.toast("在「内容下载」中把类型切到“资源包”即可浏览下载");
        });
        HBox toolbar = new HBox(10, Ui.label("实例", "small", "muted"), instanceBox,
                Ui.spacer(), download, open, add);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox top = new VBox(10, header, toolbar, pathLabel);
        view.setTop(top);
        view.setCenter(scroll);
        view.setBottom(summary);
        BorderPane.setMargin(summary, new Insets(8, 0, 0, 2));
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
        List<String> names = new ArrayList<>();
        for (LocalVersions.Installed installed : instances) {
            names.add(installed.id());
        }
        instanceBox.getItems().setAll(names);
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
        if (!names.isEmpty()) {
            instanceBox.getSelectionModel().select(index);
            selectInstance();
        } else {
            list.getChildren().setAll(Ui.small("还没有安装任何版本"));
        }
    }

    private void selectInstance() {
        int index = instanceBox.getSelectionModel().getSelectedIndex();
        if (index < 0 || index >= instances.size()) {
            return;
        }
        current = instances.get(index);
        context.select(current);
        pathLabel.setText("目录: " + resourceDir());
        summary.setText("");
        rebuild();
    }

    private Path resourceDir() {
        if (current == null) {
            return context.settings.mcPath().resolve("resourcepacks");
        }
        InstanceSettings settings = context.instanceSettings(current);
        Path gameDir = settings.isolated ? current.dir() : context.settings.mcPath();
        return gameDir.resolve("resourcepacks");
    }

    private void rebuild() {
        list.getChildren().clear();
        Path dir = resourceDir();
        if (!Files.isDirectory(dir)) {
            list.getChildren().add(new VBox(6,
                    Ui.small("这个实例还没有资源包文件夹"),
                    Ui.small("点击“添加资源包”或把 zip 放进文件夹")));
            return;
        }
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(Files::isRegularFile).sorted().toList();
        } catch (Exception e) {
            list.getChildren().add(Ui.small("无法读取资源包目录: " + e.getMessage()));
            return;
        }
        int enabled = 0;
        for (Path file : files) {
            String name = file.getFileName().toString();
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".zip") && !lower.endsWith(".zip.disabled")) {
                continue;
            }
            boolean on = lower.endsWith(".zip");
            if (on) {
                enabled++;
            }
            HBox row = new HBox(10);
            row.setPadding(new Insets(8, 10, 8, 10));
            row.getStyleClass().add("row-hover");
            row.setAlignment(Pos.CENTER_LEFT);

            Label title = Ui.label(on ? name : name.substring(0, name.length() - ".disabled".length()), "small");
            HBox.setHgrow(title, Priority.ALWAYS);
            Label state = Ui.chip(on ? "已启用" : "已禁用", on ? "chip-green" : "chip-red");
            Button toggle = new Button(on ? "禁用" : "启用");
            toggle.getStyleClass().addAll("btn", "btn-small");
            toggle.setOnAction(event -> toggle(file, on));
            Button delete = new Button();
            delete.setGraphic(Icon.of(Icon.TRASH, 13, Ui.RED));
            delete.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            delete.setOnAction(event -> delete(file));
            row.getChildren().addAll(packIcon(file), title, state, toggle, delete);
            list.getChildren().add(row);
        }
        if (list.getChildren().isEmpty()) {
            list.getChildren().add(Ui.small("资源包目录是空的"));
        }
        summary.setText(enabled + " 个资源包已启用");
    }

    private javafx.scene.Node packIcon(Path file) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file.toFile())) {
            var entry = zip.getEntry("pack.png");
            if (entry != null) {
                try (var in = zip.getInputStream(entry)) {
                    byte[] bytes = in.readAllBytes();
                    javafx.scene.image.Image image = new javafx.scene.image.Image(
                            new java.io.ByteArrayInputStream(bytes), 72, 72, true, true);
                    if (!image.isError()) {
                        javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(image);
                        view.setFitWidth(36);
                        view.setFitHeight(36);
                        view.setPreserveRatio(true);
                        view.setSmooth(true);
                        return view;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return Icon.of(Icon.PACKAGE, 14, Ui.TEXT_FAINT);
    }

    private void toggle(Path file, boolean enabled) {        String name = file.getFileName().toString();
        Path target = enabled
                ? file.resolveSibling(name + ".disabled")
                : file.resolveSibling(name.substring(0, name.length() - ".disabled".length()));
        try {
            Files.move(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            rebuild();
        } catch (Exception e) {
            Util.error(host.window(), "操作失败", e.getMessage());
        }
    }

    private void delete(Path file) {
        if (!Util.confirm(host.window(), "删除资源包", "确定删除 " + file.getFileName() + " 吗？")) {
            return;
        }
        try {
            Files.deleteIfExists(file);
            rebuild();
        } catch (Exception e) {
            Util.error(host.window(), "删除失败", e.getMessage());
        }
    }

    private void addPacks() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择资源包");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("资源包 (*.zip)", "*.zip"));
        List<File> files = chooser.showOpenMultipleDialog(host.window());
        if (files == null || files.isEmpty()) {
            return;
        }
        Path dir = resourceDir();
        int count = 0;
        for (File file : files) {
            try {
                Files.createDirectories(dir);
                Files.copy(file.toPath(), dir.resolve(file.getName()),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                count++;
            } catch (Exception e) {
                Util.error(host.window(), "添加失败", e.getMessage());
            }
        }
        rebuild();
        host.toast("已添加 " + count + " 个资源包");
    }
}
