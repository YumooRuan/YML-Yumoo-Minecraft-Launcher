package dev.yml.ui.component;

import dev.yml.ui.Ui;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Files;
import java.nio.file.Path;

/** Floating console that tails the running game's output. */
public final class LogWindow {

    private static LogWindow instance;

    private final Stage stage;
    private final TextArea area = new TextArea();
    private final HBox header;
    private Path logFile;
    private int lineCount;
    private Runnable analyzeAction;
    private javafx.animation.Timeline follower;
    private Path followed;
    private long readOffset;
    private final StringBuilder partial = new StringBuilder();

    private LogWindow(Window owner) {
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle("游戏日志 - Yumoo Minecraft Launcher");
        stage.setWidth(860);
        stage.setHeight(520);

        area.setEditable(false);
        area.setWrapText(false);
        area.getStyleClass().add("mono");
        area.setStyle("-fx-font-size: 12px; -fx-control-inner-background: #0e0e12; -fx-text-fill: #d6d6e0;");
        area.setPadding(new Insets(6));

        Button copy = new Button("复制");
        copy.getStyleClass().addAll("btn", "btn-small");
        copy.setOnAction(event -> {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(area.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        Button clear = new Button("清空");
        clear.getStyleClass().addAll("btn", "btn-small");
        clear.setOnAction(event -> {
            area.clear();
            lineCount = 0;
        });
        Button openFile = new Button("打开日志文件夹");
        openFile.getStyleClass().addAll("btn", "btn-small");
        openFile.setOnAction(event -> {
            if (logFile != null) {
                dev.yml.ui.Util.openFolder(logFile.getParent());
            }
        });
        Button analyze = new Button("分析日志");
        analyze.getStyleClass().addAll("btn", "btn-small");
        analyze.setOnAction(event -> {
            if (analyzeAction != null) {
                analyzeAction.run();
            }
        });
        header = Ui.row(8, Ui.label("实时输出", "small", "muted"), Ui.spacer(), analyze, openFile, clear, copy);
        header.setPadding(new Insets(8, 10, 8, 10));

        BorderPane root = new BorderPane(area);
        root.setTop(header);
        root.setStyle("-fx-background-color: #121216;");
        Scene scene = new Scene(root);
        var css = LogWindow.class.getResource("/ui/theme.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            event.consume();
            stage.hide();
        });
    }

    public static LogWindow get(Window owner) {
        if (instance == null) {
            instance = new LogWindow(owner);
        }
        return instance;
    }

    public void setLogFile(Path file) {
        this.logFile = file;
    }

    public void setAnalyzeAction(Runnable action) {
        this.analyzeAction = action;
    }

    /** Tails the game's own {@code logs/latest.log} (modern versions do not print to stdout). */
    public void follow(Path file) {
        stopFollow();
        followed = file;
        readOffset = 0;
        partial.setLength(0);
        follower = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(600), event -> poll()));
        follower.setCycleCount(javafx.animation.Animation.INDEFINITE);
        follower.play();
        poll();
    }

    public void stopFollow() {
        if (follower != null) {
            follower.stop();
            follower = null;
        }
        followed = null;
    }

    private void poll() {
        if (followed == null || !Files.isRegularFile(followed)) {
            return;
        }
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(followed.toFile(), "r")) {
            long length = file.length();
            if (length < readOffset) {
                readOffset = 0;
                partial.setLength(0);
            }
            if (length == readOffset) {
                return;
            }
            file.seek(readOffset);
            int size = (int) Math.min(length - readOffset, 512 * 1024);
            byte[] buffer = new byte[size];
            int read = file.read(buffer);
            if (read <= 0) {
                return;
            }
            readOffset += read;
            partial.append(new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8));
            int index;
            while ((index = partial.indexOf("\n")) >= 0) {
                String line = partial.substring(0, index).replace("\r", "");
                partial.delete(0, index + 1);
                if (!line.isBlank()) {
                    append(dev.yml.core.diagnose.LogAnalyzer.stripAnsi(line));
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void append(String line) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> append(line));
            return;
        }
        area.appendText(line + "\n");
        lineCount++;
        if (lineCount > 4000) {
            area.deleteText(0, Math.min(area.getLength(), 200_000));
            lineCount = 1200;
        }
        area.selectPositionCaret(area.getLength());
        area.deselect();
    }

    public void show() {
        if (!stage.isShowing()) {
            stage.show();
        }
        stage.toFront();
    }

    public void hide() {
        stopFollow();
        stage.hide();
    }
}
