package dev.yml.ui.page;

import dev.yml.core.config.InstanceSettings;
import dev.yml.core.game.LocalVersions;
import dev.yml.core.game.ServersDat;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Add/remove multiplayer servers in servers.dat without opening the game. */
public final class ServersPage implements Page {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane view = new BorderPane();
    private final ComboBox<String> instanceBox = new ComboBox<>();
    private final VBox list = new VBox(4);
    private final TextField nameField = new TextField();
    private final TextField ipField = new TextField();
    private final Label status = Ui.label("", "tiny", "faint");
    private final List<LocalVersions.Installed> instances = new ArrayList<>();
    private LocalVersions.Installed current;
    private final List<ServersDat.Server> servers = new ArrayList<>();

    public ServersPage(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        view.setPadding(new Insets(18, 20, 14, 20));
        HBox header = Ui.row(10, Ui.h1("服务器管理"), Ui.small("直接编辑 servers.dat，启动游戏后同步"));
        header.setAlignment(Pos.CENTER_LEFT);
        instanceBox.setPrefWidth(280);
        instanceBox.setOnAction(event -> selectInstance());
        HBox toolbar = new HBox(10, Ui.label("实例", "small", "muted"), instanceBox);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        nameField.setPromptText("服务器名称");
        nameField.setPrefWidth(150);
        ipField.setPromptText("地址，例如 play.example.com:25565");
        ipField.setPrefWidth(300);
        Button add = new Button("添加服务器");
        add.getStyleClass().addAll("btn", "btn-primary", "btn-small");
        add.setOnAction(event -> addServer());
        HBox addRow = new HBox(8, nameField, ipField, add);
        addRow.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        view.setTop(new VBox(10, header, toolbar, addRow));
        view.setCenter(scroll);
        view.setBottom(status);
        BorderPane.setMargin(status, new Insets(8, 0, 0, 2));
    }

    private Path serversFile() {
        if (current == null) {
            return context.settings.mcPath().resolve("servers.dat");
        }
        InstanceSettings settings = context.instanceSettings(current);
        Path dir = settings.isolated ? current.dir() : context.settings.mcPath();
        return dir.resolve("servers.dat");
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
        servers.clear();
        servers.addAll(ServersDat.load(serversFile()));
        render();
    }

    private void render() {
        list.getChildren().clear();
        for (ServersDat.Server server : servers) {
            HBox row = new HBox(10);
            row.setPadding(new Insets(8, 10, 8, 10));
            row.getStyleClass().add("row-hover");
            row.setAlignment(Pos.CENTER_LEFT);
            Label name = Ui.label(server.name(), "small");
            name.setMinWidth(160);
            Label ip = Ui.label(server.ip(), "tiny", "faint");
            HBox.setHgrow(ip, Priority.ALWAYS);
            Button delete = new Button("删除");
            delete.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            delete.setOnAction(event -> {
                servers.remove(server);
                save();
                render();
            });
            row.getChildren().addAll(name, ip, delete);
            list.getChildren().add(row);
        }
        if (servers.isEmpty()) {
            list.getChildren().add(Ui.small("还没有保存的服务器"));
        }
        status.setText(servers.size() + " 个服务器 · " + serversFile());
    }

    private void addServer() {
        String ip = ipField.getText() == null ? "" : ipField.getText().trim();
        if (ip.isBlank()) {
            host.toast("服务器地址不能为空");
            return;
        }
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        servers.add(new ServersDat.Server(name.isBlank() ? ip : name, ip));
        nameField.clear();
        ipField.clear();
        save();
        render();
    }

    private void save() {
        try {
            Files.createDirectories(serversFile().getParent());
            ServersDat.save(serversFile(), servers);
            host.toast("服务器列表已保存，启动游戏后生效");
        } catch (Exception e) {
            Util.error(host.window(), "保存失败", e.getMessage());
        }
    }
}
