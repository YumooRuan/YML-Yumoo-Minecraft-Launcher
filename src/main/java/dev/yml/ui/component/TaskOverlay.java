package dev.yml.ui.component;

import dev.yml.ui.Ui;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Dimmed progress card shown while a long task runs. */
public final class TaskOverlay extends StackPane {

    private final Label titleLabel = Ui.h3("正在处理");
    private final Label noteLabel = Ui.small("");
    private final Label speedLabel = Ui.label("", "tiny", "faint");
    private final ProgressBar bar = new ProgressBar(0);
    private final Button cancelButton = new Button("取消");
    private final VBox card = new VBox(12);
    private Runnable cancelAction;

    public TaskOverlay() {
        setStyle("-fx-background-color: rgba(8, 8, 12, 0.66);");
        card.getStyleClass().add("card");
        card.setPadding(new Insets(22));
        card.setSpacing(12);
        card.setMaxWidth(460);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        bar.setMaxWidth(Double.MAX_VALUE);
        noteLabel.setWrapText(true);
        noteLabel.setMaxWidth(320);
        speedLabel.setMinWidth(80);
        speedLabel.setAlignment(Pos.CENTER_RIGHT);
        javafx.scene.layout.HBox infoRow = new javafx.scene.layout.HBox(10, noteLabel,
                Ui.spacer(), speedLabel);
        infoRow.setAlignment(Pos.CENTER_LEFT);
        cancelButton.getStyleClass().add("btn-small");
        javafx.scene.layout.HBox buttons = new javafx.scene.layout.HBox(8, Ui.spacer(), cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        cancelButton.setOnAction(event -> {
            if (cancelAction != null) {
                cancelAction.run();
            }
            cancelButton.setDisable(true);
            noteLabel.setText("正在取消...");
        });
        card.getChildren().addAll(titleLabel, infoRow, bar, buttons);
        StackPane.setAlignment(card, Pos.CENTER);
        getChildren().add(card);
        setVisible(false);
        setManaged(false);
        setOnMouseClicked(event -> event.consume());
    }

    public void show(String title, String note, Runnable onCancel) {
        titleLabel.setText(title);
        noteLabel.setText(note == null ? "" : note);
        speedLabel.setText("");
        bar.setProgress(-1);
        cancelButton.setDisable(false);
        cancelButton.setVisible(onCancel != null);
        cancelAction = onCancel;
        setVisible(true);
        setManaged(true);
        toFront();
    }

    public void update(double progress, String note) {
        Runnable task = () -> {
            if (!isVisible()) {
                return;
            }
            if (progress >= 0) {
                bar.setProgress(Math.min(1, progress));
            }
            if (note != null && !note.isBlank()) {
                noteLabel.setText(note);
            }
        };
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    public void updateSpeed(long bytesPerSecond) {
        Runnable task = () -> {
            if (isVisible()) {
                speedLabel.setText(dev.yml.core.util.RateMeter.format(bytesPerSecond));
            }
        };
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
        speedLabel.setText("");
        cancelAction = null;
    }
}
