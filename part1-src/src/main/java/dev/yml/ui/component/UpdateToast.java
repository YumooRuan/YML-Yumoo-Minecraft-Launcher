package dev.yml.ui.component;

import dev.yml.core.update.UpdateService;
import dev.yml.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Small non-modal update card pinned to the bottom-right corner. It only occupies its own bounds,
 * so the rest of the launcher stays clickable.
 */
public final class UpdateToast extends HBox {

    public UpdateToast(UpdateService.Info info, Runnable onDownload, Runnable onSkip, Runnable onClose) {
        getStyleClass().add("card");
        setPadding(new Insets(12, 14, 12, 14));
        setSpacing(10);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setPrefWidth(360);
        setAlignment(Pos.TOP_LEFT);

        Label title = Ui.label("发现新版本 " + info.version(), "h3");
        Label notes = Ui.label(info.notes() == null || info.notes().isBlank()
                ? "当前版本 " + UpdateService.CURRENT_VERSION + "，可以更新到最新版。"
                : info.notes(), "tiny", "faint");
        notes.setWrapText(true);
        notes.setMaxWidth(250);
        VBox text = new VBox(4, title, notes);

        Button download = new Button("下载更新");
        download.getStyleClass().addAll("btn", "btn-primary", "btn-small");
        download.setOnAction(event -> onDownload.run());
        Button skip = new Button("忽略此版本");
        skip.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        skip.setOnAction(event -> onSkip.run());
        HBox buttons = new HBox(8, download, skip);
        buttons.setAlignment(Pos.CENTER_LEFT);
        text.getChildren().add(buttons);

        Button close = new Button("×");
        close.setStyle("-fx-background-color: transparent; -fx-text-fill: #9a9aa8; -fx-font-size: 16px;"
                + " -fx-cursor: hand; -fx-padding: 0 2 0 2;");
        close.setOnAction(event -> onClose.run());
        HBox.setHgrow(text, Priority.ALWAYS);
        getChildren().addAll(text, close);
    }
}
