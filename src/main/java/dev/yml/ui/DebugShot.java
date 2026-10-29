package dev.yml.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Development helper: renders the scene to a PNG so the UI can be inspected without a display. */
public final class DebugShot {

    private DebugShot() {
    }

    public static void capture(Stage stage, String path, long delayMillis, Runnable onDone) {
        PauseTransition pause = new PauseTransition(Duration.millis(delayMillis));
        pause.setOnFinished(event -> {
            try {
                WritableImage image = stage.getScene().snapshot(null);
                int width = (int) image.getWidth();
                int height = (int) image.getHeight();
                BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                PixelReader reader = image.getPixelReader();
                int[] row = new int[width];
                for (int y = 0; y < height; y++) {
                    reader.getPixels(0, y, width, 1, PixelFormat.getIntArgbInstance(), row, 0, width);
                    buffered.setRGB(0, y, width, 1, row, 0, width);
                }
                File file = new File(path);
                File parent = file.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                ImageIO.write(buffered, "png", file);
                System.out.println("[shot] " + file.getAbsolutePath() + " " + width + "x" + height);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (onDone != null) {
                onDone.run();
            } else {
                Platform.exit();
            }
        });
        pause.play();
    }
}
