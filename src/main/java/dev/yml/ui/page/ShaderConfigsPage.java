package dev.yml.ui.page;

import dev.yml.core.config.InstanceSettings;
import dev.yml.core.game.LocalVersions;
import dev.yml.core.game.ShaderConfigs;
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
import javafx.stage.FileChooser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Import / export PCL2-compatible shader settings files. */
public final class ShaderConfigsPage implements Page {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane view = new BorderPane();
    private final ComboBox<String> instanceBox = new ComboBox<>();
    private final VBox list = new VBox(6);
    private final Label status = Ui.label("", "tiny", "faint");
    private final List<LocalVersions.Installed> instances = new ArrayList<>();
    private LocalVersions.Installed current;

    public ShaderConfigsPage(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        view.setPadding(new Insets(18, 20, 14, 20));
        HBox header = Ui.row(10, Ui.h1("光影配置"),
                Ui.small("兼容 PCL2：读取/写入 shaderpacks/<光影名>.txt"));
        header.setAlignment(Pos.CENTER_LEFT);

        instanceBox.setPrefWidth(300);
        instanceBox.setOnAction(event -> selectInstance());
        Button open = new Button("打开 shaderpacks");
        open.getStyleClass().addAll("btn", "btn-small");
        open.setOnAction(event -> Util.openFolder(shaderDir()));
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

    private Path shaderDir() {
        if (current == null) {
            return context.settings.mcPath().resolve("shaderpacks");
        }
        InstanceSettings settings = context.instanceSettings(current);
        Path dir = settings.isolated ? current.dir() : context.settings.mcPath();
        return dir.resolve("shaderpacks");
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
        Path dir = shaderDir();
        List<ShaderConfigs.Pack> packs = ShaderConfigs.scan(dir);
        if (packs.isEmpty()) {
            list.getChildren().add(Ui.small("该实例还没有光影包，请先在下载页安装光影。"));
            status.setText("目录: " + dir);
            return;
        }
        for (ShaderConfigs.Pack pack : packs) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(9, 10, 9, 10));
            row.getStyleClass().add("row-hover");
            Label name = Ui.label(pack.name(), "small");
            name.setMaxWidth(300);
            HBox.setHgrow(name, Priority.ALWAYS);
            Label state = pack.hasConfig()
                    ? Ui.chip("配置 " + ShaderConfigs.countOptions(pack.config()) + " 项", "chip-green")
                    : Ui.chip("无配置", "chip-red");
            Button importButton = new Button("导入配置");
            importButton.getStyleClass().addAll("btn", "btn-small");
            importButton.setOnAction(event -> importConfig(pack));
            Button exportButton = new Button("导出配置");
            exportButton.getStyleClass().addAll("btn", "btn-small");
            exportButton.setDisable(!pack.hasConfig());
            exportButton.setOnAction(event -> exportConfig(pack));
            row.getChildren().addAll(Ui.chip(pack.file().getFileName().toString().toLowerCase().endsWith(".jar")
                            ? "JAR" : "ZIP"),
                    name, state, importButton, exportButton);
            list.getChildren().add(row);
        }
        status.setText(packs.size() + " 个光影包 · 目录: " + dir
                + " · 配置文件与 PCL2 通用");
    }

    private void importConfig(ShaderConfigs.Pack pack) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择光影配置（PCL2 的 .txt）");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "光影配置 (*.txt)", "*.txt"));
        java.io.File file = chooser.showOpenDialog(host.window());
        if (file == null) {
            return;
        }
        try {
            ShaderConfigs.importConfig(file.toPath(), pack);
            render();
            host.toast("已导入配置：" + file.getName());
        } catch (Exception e) {
            Util.error(host.window(), "导入失败", e.getMessage());
        }
    }

    private void exportConfig(ShaderConfigs.Pack pack) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出光影配置");
        chooser.setInitialFileName(pack.name() + ".txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "光影配置 (*.txt)", "*.txt"));
        java.io.File file = chooser.showSaveDialog(host.window());
        if (file == null) {
            return;
        }
        try {
            ShaderConfigs.exportConfig(pack, file.toPath());
            host.toast("已导出配置：" + file.getName());
        } catch (Exception e) {
            Util.error(host.window(), "导出失败", e.getMessage());
        }
    }
}
