package dev.yml.ui.component;

import dev.yml.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/** Reusable undecorated modal window with the launcher's title bar and footer. */
public final class DialogShell {

    public final Stage stage = new Stage();
    private final BorderPane body = new BorderPane();
    private final HBox footer = new HBox(8);
    private final VBox root = new VBox();

    public DialogShell(Window owner, String title, double width, double height) {
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle(title);
        stage.getIcons().add(TitleBar.appIcon(32));

        TitleBar titleBar = new TitleBar(stage);
        javafx.scene.control.Label label = Ui.label(title, "h3");
        javafx.scene.layout.HBox header = new HBox(10, label, Ui.spacer(),
                new javafx.scene.control.Label());
        // Replace the default title bar text with the dialog title.
        ((javafx.scene.control.Label) titleBar.getChildren().get(1)).setText(title);
        ((javafx.scene.control.Label) titleBar.getChildren().get(2)).setText("");
        // Dialogs keep only the close button, like a Windows message box.
        titleBar.getChildren().get(4).setVisible(false);
        titleBar.getChildren().get(4).setManaged(false);
        titleBar.getChildren().get(5).setVisible(false);
        titleBar.getChildren().get(5).setManaged(false);

        body.setPadding(new Insets(14, 16, 6, 16));
        footer.setPadding(new Insets(8, 16, 12, 16));
        footer.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().addAll(titleBar, body, footer);
        root.setStyle("-fx-background-color: #202024; -fx-border-color: #3c3c44; -fx-border-width: 1;");
        root.getStyleClass().add("flat-dialog");

        Scene scene = new Scene(root, width, height);
        var css = DialogShell.class.getResource("/ui/theme.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.setMinWidth(width);
        stage.setMinHeight(height);
        stage.setOnShown(event -> {
            root.setOpacity(0);
            javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(
                    javafx.util.Duration.millis(140), root);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        });
        if (owner != null) {
            stage.setX(owner.getX() + (owner.getWidth() - width) / 2);
            stage.setY(owner.getY() + (owner.getHeight() - height) / 2);
        }
        header.setVisible(false);
        header.setManaged(false);
    }

    public void setContent(Node content) {
        body.setCenter(content);
    }

    public void setFooter(Node... nodes) {
        footer.getChildren().setAll(nodes);
    }

    public void show() {
        stage.show();
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    public void close() {
        stage.close();
    }
}
