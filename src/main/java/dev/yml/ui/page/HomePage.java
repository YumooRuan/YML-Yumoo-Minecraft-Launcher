package dev.yml.ui.page;

import dev.yml.core.config.InstanceSettings;
import dev.yml.core.game.LocalVersions;
import dev.yml.ui.AppContext;
import dev.yml.ui.Page;
import dev.yml.ui.Ui;
import dev.yml.ui.Util;
import dev.yml.ui.component.CubeIcon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;

/** Clean landing page: selected version, launch and quick shortcuts in the corner. */
public final class HomePage implements Page {

    private final VersionsPage.Host host;
    private final AppContext context;
    private final BorderPane view = new BorderPane();
    private final VBox hero = new VBox(12);
    private final StackPane iconHolder = new StackPane();
    private final Label name = Ui.label("还没有安装版本", "h1");
    private final HBox meta = new HBox(6);
    private final Label hint = Ui.label("前往版本管理安装一个版本", "small", "muted");
    private final Button launch = new Button("启动游戏");
    private final Button switchVersion = new Button("版本切换");
    private final Button settings = new Button("版本设置");

    public HomePage(VersionsPage.Host host, AppContext context) {
        this.host = host;
        this.context = context;
        build();
    }

    private void build() {
        view.setPadding(new Insets(28, 32, 24, 32));
        view.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        view.setMinSize(0, 0);

        Label brand = Ui.label("YUMOO MINECRAFT LAUNCHER", "tiny", "faint");
        brand.setStyle("-fx-font-size: 11.5px;");
        iconHolder.setMinSize(96, 96);
        iconHolder.setPrefSize(96, 96);
        iconHolder.setMaxSize(96, 96);
        iconHolder.setCursor(javafx.scene.Cursor.HAND);
        Ui.tooltip(iconHolder, "点击更换实例图标（PNG / JPG / JPEG）");
        iconHolder.setOnMouseClicked(event -> chooseIcon());

        name.setWrapText(true);
        name.setMaxWidth(Double.MAX_VALUE);
        name.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");
        name.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        name.setAlignment(Pos.CENTER);
        meta.setAlignment(Pos.CENTER);
        hint.setWrapText(true);
        hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        hint.setMaxWidth(520);
        hint.setAlignment(Pos.CENTER);

        hero.setAlignment(Pos.CENTER);
        hero.setFillWidth(true);
        hero.getChildren().addAll(brand, iconHolder, name, meta, hint);
        StackPane center = new StackPane(hero);
        center.setAlignment(Pos.CENTER);

        launch.getStyleClass().addAll("btn", "btn-primary");
        launch.setGraphic(dev.yml.ui.component.Icon.of(
                dev.yml.ui.component.Icon.PLAY, 15, Ui.TEXT));
        launch.setStyle("-fx-font-size: 15px; -fx-padding: 11 26 11 22;");
        launch.setOnAction(event -> {
            LocalVersions.Installed selected = context.selected();
            if (selected != null) {
                host.launch(selected);
            }
        });

        switchVersion.getStyleClass().addAll("btn");
        switchVersion.setGraphic(dev.yml.ui.component.Icon.of(
                dev.yml.ui.component.Icon.REFRESH, 13, Ui.TEXT_DIM));
        switchVersion.setOnAction(event -> showVersionMenu());

        settings.getStyleClass().addAll("btn");
        settings.setGraphic(dev.yml.ui.component.Icon.of(
                dev.yml.ui.component.Icon.SETTINGS, 13, Ui.TEXT_DIM));
        settings.setOnAction(event -> {
            LocalVersions.Installed selected = context.selected();
            if (selected == null) {
                host.navigate("versions");
            } else {
                host.openInstanceSettings(selected);
            }
        });

        HBox actions = new HBox(10, settings, switchVersion, launch);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox actionRow = new HBox(actions);
        actionRow.setAlignment(Pos.CENTER_RIGHT);
        actionRow.setPadding(new Insets(0, 4, 4, 0));
        view.setCenter(center);
        view.setBottom(actionRow);
    }

    private void showVersionMenu() {
        ContextMenu menu = new ContextMenu();
        for (LocalVersions.Installed installed : context.versions()) {
            String label = installed.id() + "  ·  " + installed.loader().label();
            MenuItem item = new MenuItem(label);
            item.setOnAction(event -> {
                context.select(installed);
                refresh();
                host.toast("已切换版本：" + installed.id());
            });
            menu.getItems().add(item);
        }
        if (menu.getItems().isEmpty()) {
            MenuItem empty = new MenuItem("还没有安装版本");
            empty.setDisable(true);
            menu.getItems().add(empty);
        }
        menu.getItems().add(new SeparatorMenuItem());
        MenuItem manage = new MenuItem("管理版本...");
        manage.setOnAction(event -> host.navigate("versions"));
        menu.getItems().add(manage);
        menu.show(switchVersion, Side.TOP, 0, -6);
    }

    @Override
    public Node view() {
        return view;
    }

    @Override
    public void onShow() {
        refresh();
    }

    public void refresh() {
        LocalVersions.Installed selected = context.selected();
        meta.getChildren().clear();
        iconHolder.getChildren().clear();
        if (selected == null) {
            name.setText("还没有安装版本");
            hint.setText("前往版本管理安装一个版本");
            meta.getChildren().add(Ui.chip("未选择版本", "chip-red"));
            iconHolder.getChildren().add(Ui.label("", "h1"));
            launch.setDisable(true);
            return;
        }
        launch.setDisable(false);
        name.setText(selected.id());
        if (selected.broken()) {
            hint.setText("版本文件不完整，请到版本管理检查");
            meta.getChildren().add(Ui.chip("无法读取", "chip-red"));
        } else {
            hint.setText("选择版本后可直接启动，或前往版本管理调整设置");
            meta.getChildren().add(Ui.chip(selected.loader().label(),
                    chipFor(selected.loader())));
            meta.getChildren().add(Ui.chip(selected.baseVersion()));
        }
        iconHolder.getChildren().add(versionIcon(selected, 88));
    }

    private Node versionIcon(LocalVersions.Installed installed, double size) {
        Path icon = customIcon(gameDir(installed));
        if (icon != null) {
            try {
                Image image = new Image(icon.toUri().toString(), size, size, true, true);
                if (!image.isError()) {
                    ImageView view = new ImageView(image);
                    view.setFitWidth(size);
                    view.setFitHeight(size);
                    view.setPreserveRatio(true);
                    view.setSmooth(true);
                    return view;
                }
            } catch (Exception ignored) {
            }
        }
        return new CubeIcon(installed.loader(), size);
    }

    private void chooseIcon() {
        LocalVersions.Installed selected = context.selected();
        if (selected == null) {
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("选择实例图标");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                "图片 (*.png, *.jpg, *.jpeg)", "*.png", "*.jpg", "*.jpeg"));
        javafx.stage.Window dialogOwner = iconHolder.getScene() == null
                ? null : iconHolder.getScene().getWindow();
        java.io.File file = chooser.showOpenDialog(dialogOwner);
        if (file == null) {
            return;
        }
        try {
            Path dir = gameDir(selected);
            Files.createDirectories(dir);
            for (String ext : new String[]{"png", "jpg", "jpeg"}) {
                Files.deleteIfExists(dir.resolve("icon." + ext));
            }
            String name = file.getName().toLowerCase(java.util.Locale.ROOT);
            String ext = name.endsWith(".jpg") || name.endsWith(".jpeg") ? "jpg" : "png";
            Files.copy(file.toPath(), dir.resolve("icon." + ext),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            refresh();
            host.toast("已设置实例图标");
        } catch (Exception e) {
            Util.error(dialogOwner, "设置图标失败", e.getMessage());
        }
    }

    private static Path customIcon(Path gameDir) {
        for (String ext : new String[]{"png", "jpg", "jpeg"}) {
            Path icon = gameDir.resolve("icon." + ext);
            if (Files.isRegularFile(icon)) {
                return icon;
            }
        }
        return null;
    }

    private Path gameDir(LocalVersions.Installed installed) {
        InstanceSettings instance = context.instanceSettings(installed);
        return instance.isolated ? installed.dir() : context.settings.mcPath();
    }

    private static String chipFor(dev.yml.core.game.LoaderType loader) {
        return switch (loader) {
            case VANILLA -> "chip-blue";
            case FABRIC -> "chip-orange";
            case FORGE -> "chip-purple";
            case NEOFORGE -> "chip-green";
            case QUILT -> "chip-blue";
            case OPTIFINE -> "chip-orange";
            default -> "chip-blue";
        };
    }
}
