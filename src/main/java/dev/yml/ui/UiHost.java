package dev.yml.ui;

import dev.yml.core.util.Progress;
import javafx.stage.Window;

import java.util.function.Consumer;

/** Callbacks pages use to talk to the main window. */
public interface UiHost {

    void showPage(String id);

    void toast(String message);

    void refreshAll();

    void openLog();

    Window window();

    /** Runs a background task with the shared progress overlay and error dialog. */
    void runTask(String title, Consumer<Progress> task, Runnable onDone);

    /** Re-applies the configured window background. */
    void refreshBackground();

    /** Copies an image into launcher data and uses it as the background. */
    boolean importBackgroundImage(java.nio.file.Path file);

    /** Copies the current desktop wallpaper (including Wallpaper Engine frames) into launcher data. */
    java.nio.file.Path syncWallpaper() throws java.io.IOException;

    /** Re-applies the selected launcher language to the whole UI. */
    void applyLanguage();
}
