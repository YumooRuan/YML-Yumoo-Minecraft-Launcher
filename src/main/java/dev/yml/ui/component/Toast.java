package dev.yml.ui.component;

import dev.yml.ui.Ui;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/** Non-blocking toast shown at the bottom-right; disappears automatically. */
public final class Toast {

    private Toast() {
    }

    public static void show(StackPane root, String message) {
        if (root == null || message == null || message.isBlank()) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(root, message));
            return;
        }
        root.getChildren().removeIf(node -> "yml-toast".equals(node.getId()));

        Label text = new Label(message);
        text.setWrapText(true);
        text.setMaxWidth(420);
        text.setStyle("-fx-text-fill: #f0f0f5; -fx-font-size: 13px;");

        HBox toast = new HBox(9, Icon.of(Icon.CHECK, 15, Ui.GREEN), text);
        toast.setId("yml-toast");
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setPadding(new Insets(11, 16, 11, 14));
        toast.setMaxWidth(Region.USE_PREF_SIZE);
        toast.setMaxHeight(Region.USE_PREF_SIZE);
        toast.setMouseTransparent(true);
        toast.setPickOnBounds(false);
        toast.setStyle("-fx-background-color: #202028; -fx-background-radius: 10;"
                + "-fx-border-color: #3c3c48; -fx-border-radius: 10;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 18, 0.25, 0, 6);");
        StackPane.setAlignment(toast, Pos.TOP_RIGHT);
        StackPane.setMargin(toast, new Insets(54, 24, 0, 0));

        root.getChildren().add(toast);
        toast.setOpacity(0);
        toast.setTranslateY(-8);

        FadeTransition in = new FadeTransition(Duration.millis(150), toast);
        in.setFromValue(0);
        in.setToValue(1);
        TranslateTransition up = new TranslateTransition(Duration.millis(180), toast);
        up.setFromY(-8);
        up.setToY(0);
        PauseTransition stay = new PauseTransition(Duration.millis(2300));
        FadeTransition out = new FadeTransition(Duration.millis(180), toast);
        out.setFromValue(1);
        out.setToValue(0);
        SequentialTransition sequence = new SequentialTransition(
                new javafx.animation.ParallelTransition(in, up), stay, out);
        sequence.setOnFinished(event -> {
            toast.setMouseTransparent(true);
            if (toast.getParent() instanceof StackPane pane) {
                pane.getChildren().remove(toast);
            }
        });
        sequence.play();
    }}
