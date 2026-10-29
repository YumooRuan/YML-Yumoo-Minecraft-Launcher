package dev.yml.ui;

import dev.yml.ui.component.DialogShell;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;

/** Flat message dialogs and desktop helpers. */
public final class Util {

    private Util() {
    }

    public static void error(Window owner, String title, String message) {
        message(owner, title, message, null, null);
    }

    public static void info(Window owner, String title, String message) {
        message(owner, title, message, null, null);
    }

    public static boolean confirm(Window owner, String title, String message) {
        return message(owner, title, message, "确定", "取消");
    }

    public static boolean confirm(Window owner, String title, String message,
                                  String okText, String cancelText) {
        return message(owner, title, message, okText, cancelText);
    }

    private static boolean message(Window owner, String title, String message,
                                   String okText, String cancelText) {
        int lines = Math.max(1, (message == null ? 0 : message.length()) / 30 + 1);
        int height = Math.min(250, 104 + lines * 18);
        DialogShell shell = new DialogShell(owner, title, 430, height);
        if (shell.stage.getScene() != null) {
            shell.stage.getScene().getRoot().getStyleClass().add("flat-dialog");
        }
        Label body = Ui.label(message == null ? "" : message, "small");
        body.setWrapText(true);
        body.setMaxWidth(420);
        VBox content = new VBox(body);
        content.setPadding(new Insets(2, 0, 0, 0));
        shell.setContent(content);

        boolean[] confirmed = {false};
        Button cancel = new Button(cancelText == null ? "关闭" : cancelText);
        cancel.getStyleClass().addAll("btn", "btn-flat");
        cancel.setOnAction(event -> shell.close());
        if (okText != null) {
            Button ok = new Button(okText);
            ok.getStyleClass().addAll("btn", "btn-primary", "btn-flat");
            ok.setDefaultButton(true);
            ok.setOnAction(event -> {
                confirmed[0] = true;
                shell.close();
            });
            shell.setFooter(cancel, ok);
        } else {
            shell.setFooter(cancel);
        }
        shell.showAndWait();
        return confirmed[0];
    }

    public static void openFolder(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder.toFile());
            }
        } catch (Exception ignored) {
        }
    }

    public static void openFile(Path file) {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file.toFile());
            }
        } catch (Exception ignored) {
        }
    }

    public static void openUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(java.net.URI.create(url));
            }
        } catch (Exception ignored) {
        }
    }
}
