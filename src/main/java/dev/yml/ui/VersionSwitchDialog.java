package dev.yml.ui;

import dev.yml.core.game.LocalVersions;
import dev.yml.ui.component.CubeIcon;
import dev.yml.ui.component.DialogShell;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Version picker shown from the bottom bar ("浏览 / 切换版本"). */
public final class VersionSwitchDialog {

    private final AppContext context;
    private final Consumer<LocalVersions.Installed> onPick;
    private final DialogShell shell;
    private final VBox rows = new VBox(3);
    private final TextField search = new TextField();

    public VersionSwitchDialog(Window owner, AppContext context, Consumer<LocalVersions.Installed> onPick) {
        this.context = context;
        this.onPick = onPick;
        this.shell = new DialogShell(owner, "浏览 / 切换版本", 620, 600);
        build();
    }

    private void build() {
        search.setPromptText("搜索版本或加载器");
        search.textProperty().addListener((obs, old, value) -> render(value));

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox content = new VBox(10, search, scroll);
        content.setPadding(new Insets(0, 4, 0, 0));
        shell.setContent(content);
        render("");

        Button close = new Button("关闭");
        close.getStyleClass().add("btn");
        close.setOnAction(event -> shell.close());
        shell.setFooter(close);
    }

    private void render(String filter) {
        rows.getChildren().clear();
        String query = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<LocalVersions.Installed> versions = context.versions();
        int shown = 0;
        for (LocalVersions.Installed version : versions) {
            String haystack = (version.id() + " " + version.loader().label() + " " + version.baseVersion())
                    .toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) {
                continue;
            }
            shown++;
            HBox row = new HBox(12);
            row.getStyleClass().add("version-row");
            row.setPadding(new Insets(9, 12, 9, 12));
            row.setAlignment(Pos.CENTER_LEFT);

            StackPane cube = new StackPane(new CubeIcon(version.loader(), 26));
            cube.setMinSize(30, 30);
            Label name = Ui.label(version.id(), "h3");
            name.setMaxWidth(300);
            HBox chips = new HBox(5, Ui.chip(version.loader().label()),
                    Ui.chip(version.baseVersion()));
            VBox info = new VBox(2, name, chips);
            HBox.setHgrow(info, Priority.ALWAYS);

            boolean current = context.selected() != null && context.selected().id().equals(version.id());
            Button openFolder = new Button();
            openFolder.setGraphic(dev.yml.ui.component.Icon.of(dev.yml.ui.component.Icon.FOLDER, 13,
                    Ui.TEXT_DIM));
            openFolder.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            openFolder.setTooltip(new javafx.scene.control.Tooltip("打开该实例的文件夹"));
            openFolder.setOnAction(event -> {
                event.consume();
                openInstanceFolder(version);
            });
            openFolder.setOnMouseClicked(event -> event.consume());
            row.getChildren().addAll(cube, info, openFolder, current ? Ui.chip("当前", "chip-green")
                    : Ui.label("切换", "tiny", "faint"));
            row.setOnMouseClicked(event -> {
                onPick.accept(version);
                shell.close();
            });
            rows.getChildren().add(row);
        }
        if (shown == 0) {
            rows.getChildren().add(Ui.small("没有匹配的版本"));
        }
    }

    public void show() {
        shell.showAndWait();
    }

    private void openInstanceFolder(LocalVersions.Installed version) {
        dev.yml.core.config.InstanceSettings settings = context.instanceSettings(version);
        java.nio.file.Path dir = settings.isolated ? version.dir() : context.settings.mcPath();
        Util.openFolder(dir);
    }
}
