package dev.yml.ui.component;

import dev.yml.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;

/** Custom window chrome (the stage is undecorated). */
public final class TitleBar extends HBox {

    private double dragOffsetX;
    private double dragOffsetY;

    public TitleBar(Stage stage) {
        getStyleClass().add("title-bar");
        setPrefHeight(42);
        setMinHeight(42);
        setMaxHeight(42);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(0, 8, 0, 14));
        setSpacing(8);

        javafx.scene.image.ImageView logo = new javafx.scene.image.ImageView(appIcon(18));
        logo.setSmooth(false);
        logo.setFitWidth(18);
        logo.setFitHeight(18);

        Label title = new Label("Yumoo Minecraft Launcher");
        title.getStyleClass().add("title-text");
        Label version = Ui.label("YML 1.0.0", "tiny", "faint");

        HBox minButton = windowButton(Icon.of(Icon.MINIMIZE, 12, Ui.TEXT_DIM), "最小化",
                () -> stage.setIconified(true));
        HBox maxButton = windowButton(Icon.of(Icon.MAXIMIZE, 11, Ui.TEXT_DIM), "最大化",
                () -> stage.setMaximized(!stage.isMaximized()));
        HBox closeButton = windowButton(Icon.of(Icon.CLOSE, 12, Ui.TEXT_DIM), "关闭", stage::close);
        closeButton.getStyleClass().add("win-btn-close");

        getChildren().addAll(logo, title, version, Ui.spacer(), minButton, maxButton, closeButton);

        setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !isButton(event.getTarget())) {
                dragOffsetX = event.getScreenX() - stage.getX();
                dragOffsetY = event.getScreenY() - stage.getY();
            }
        });
        setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown() && !stage.isMaximized() && dragOffsetX > 0) {
                stage.setX(event.getScreenX() - dragOffsetX);
                stage.setY(event.getScreenY() - dragOffsetY);
            }
        });
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                    && !isButton(event.getTarget())) {
                stage.setMaximized(!stage.isMaximized());
            }
        });
    }

    private static boolean isButton(Object target) {
        return target instanceof javafx.scene.Node node && node.getStyleClass().contains("win-btn");
    }

    private HBox windowButton(javafx.scene.Node icon, String tooltip, Runnable action) {
        HBox button = new HBox(icon);
        button.getStyleClass().add("win-btn");
        button.setAlignment(Pos.CENTER);
        button.setMinSize(46, 42);
        button.setPrefSize(46, 42);
        button.setCursor(Cursor.HAND);
        Ui.tooltip(button, tooltip);
        button.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                action.run();
            }
        });
        return button;
    }

    /** Launcher icon loaded from the bundled PNG. */
    public static Image appIcon(int size) {
        try (var stream = TitleBar.class.getResourceAsStream("/ui/yml-icon.png")) {
            if (stream != null) {
                Image image = new Image(stream, size, size, true, true);
                if (!image.isError()) {
                    return image;
                }
            }
        } catch (Exception ignored) {
        }
        return new WritableImage(size, size);
    }
}