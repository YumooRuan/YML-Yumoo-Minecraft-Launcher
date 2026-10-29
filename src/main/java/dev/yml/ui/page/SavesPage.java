package dev.yml.ui.page;

import dev.yml.core.config.InstanceSettings;
import dev.yml.core.game.LocalVersions;
import dev.yml.ui.AppContext;
import dev.yml.ui.Page;
import dev.yml.ui.Ui;
import dev.yml.ui.UiHost;
import dev.yml.ui.Util;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Manage the instance's saves folder without launching the game. */
public final class SavesPage implements Page {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane view = new BorderPane();
    private final ComboBox<String> instanceBox = new ComboBox<>();
    private final VBox list = new VBox(4);
    private final Label status = Ui.label("", "tiny", "faint");
    private final List<LocalVersions.Installed> instances = new ArrayList<>();
    private LocalVersions.Installed current;

    public SavesPage(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        view.setPadding(new Insets(18, 20, 14, 20));
        HBox header = Ui.row(10, Ui.h1("存档管理"), Ui.small("打开存档文件夹或直接删除存档"));
        header.setAlignment(Pos.CENTER_LEFT);
        instanceBox.setPrefWidth(300);
        instanceBox.setOnAction(event -> selectInstance());
        Button open = new Button("打开存档文件夹");
        open.getStyleClass().addAll("btn", "btn-small");
        open.setOnAction(event -> Util.openFolder(savesDir()));
        HBox toolbar = new HBox(10, Ui.label("实例", "small", "muted"), instanceBox,
                Ui.spacer(), open);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        view.setTop(new VBox(10, header, toolbar));
        view.setCenter(scroll);
        view.setBottom(status);
        BorderPane.setMargin(status, new Insets(8, 0, 0, 2));
    }

    private Path savesDir() {
        if (current == null) {
            return context.settings.mcPath().resolve("saves");
        }
        InstanceSettings settings = context.instanceSettings(current);
        Path dir = settings.isolated ? current.dir() : context.settings.mcPath();
        return dir.resolve("saves");
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
        render();
    }

    private void render() {
        list.getChildren().clear();
        Path dir = savesDir();
        if (!Files.isDirectory(dir)) {
            list.getChildren().add(Ui.small("这个实例还没有存档文件夹"));
            status.setText("目录: " + dir);
            return;
        }
        List<Path> saves;
        try (var stream = Files.list(dir)) {
            saves = stream.filter(Files::isDirectory).sorted().toList();
        } catch (Exception e) {
            list.getChildren().add(Ui.small("无法读取存档目录: " + e.getMessage()));
            return;
        }
        for (Path save : saves) {
            HBox row = new HBox(10);
            row.setPadding(new Insets(8, 10, 8, 10));
            row.getStyleClass().add("row-hover");
            row.setAlignment(Pos.CENTER_LEFT);
            Label name = Ui.label(save.getFileName().toString(), "small");
            HBox.setHgrow(name, Priority.ALWAYS);
            Button open = new Button("打开");
            open.getStyleClass().addAll("btn", "btn-small");
            open.setOnAction(event -> Util.openFolder(save));
            Button delete = new Button("删除");
            delete.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            delete.setOnAction(event -> delete(save));
            row.getChildren().addAll(name, open, delete);
            list.getChildren().add(row);
        }
        if (saves.isEmpty()) {
            list.getChildren().add(Ui.small("没有存档"));
        }
        status.setText(saves.size() + " 个存档 · 目录: " + dir);
    }

    private void delete(Path save) {
        if (!Util.confirm(host.window(), "删除存档",
                "确定删除存档“" + save.getFileName() + "”吗？此操作不可恢复。",
                "删除", "取消")) {
            return;
        }
        try {
            try (var stream = Files.walk(save)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
            render();
            host.toast("已删除存档");
        } catch (Exception e) {
            Util.error(host.window(), "删除失败", e.getMessage());
        }
    }
}
