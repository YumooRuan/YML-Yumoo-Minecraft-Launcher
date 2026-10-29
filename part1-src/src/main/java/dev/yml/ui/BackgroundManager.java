package dev.yml.ui;

import dev.yml.core.config.Settings;
import dev.yml.core.util.FileUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Renders the customizable window background.
 *
 * <p>Images (including animated video wallpapers in mp4/webm) and Windows wallpapers are
 * supported. Wallpaper Engine publishes the current frame to
 * {@code .../Themes/WallpaperEngineOverride_*.jpg}; a watcher reloads it whenever the file or
 * the registry entry changes, so wallpaper switches are reflected without restarting.</p>
 */
public final class BackgroundManager {

    private final AppContext context;
    private final StackPane view = new StackPane();
    private final MediaView mediaView = new MediaView();
    private final Region image = new Region();
    private final Region shade = new Region();

    private MediaPlayer player;
    private Timeline watcher;
    private long lastWatchStamp = -1;

    public BackgroundManager(AppContext context) {
        this.context = context;
        image.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        shade.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        mediaView.setVisible(false);
        mediaView.setMouseTransparent(true);
        image.setMouseTransparent(true);
        shade.setMouseTransparent(true);
        view.getChildren().addAll(mediaView, image, shade);
        view.setMouseTransparent(true);
        apply();
    }

    public Node view() {
        return view;
    }

    public void apply() {
        Settings settings = context.settings;
        String path = settings.backgroundImage;
        boolean hasImage = !"DEFAULT".equals(settings.backgroundMode) && path != null && !path.isBlank()
                && Files.isRegularFile(Path.of(path));

        stopMedia();
        if (!hasImage) {
            image.setStyle("-fx-background-color: #121216;");
            image.setEffect(null);
            image.setVisible(true);
            shade.setStyle("-fx-background-color: transparent;");
            stopWatcher();
            return;
        }

        Path file = Path.of(path);
        if (isVideo(file)) {
            playVideo(file);
            image.setVisible(false);
            image.setStyle("-fx-background-color: #121216;");
        } else {
            String url = file.toUri().toString().replace("\"", "%22");
            image.setStyle("-fx-background-image: url(\"" + url + "\");"
                    + " -fx-background-size: cover;"
                    + " -fx-background-position: center center;");
            image.setVisible(true);
            image.setEffect(settings.backgroundBlur > 0.4 ? new GaussianBlur(settings.backgroundBlur) : null);
        }
        double dim = clamp(settings.backgroundDim, 0.0, 0.92);
        shade.setStyle(String.format(Locale.ROOT, "-fx-background-color: rgba(8, 8, 12, %.2f);", dim));
        if ("WALLPAPER".equals(settings.backgroundMode)) {
            startWatcher();
        } else {
            stopWatcher();
        }
    }

    private void playVideo(Path file) {
        try {
            Media media = new Media(file.toUri().toString());
            player = new MediaPlayer(media);
            player.setCycleCount(MediaPlayer.INDEFINITE);
            player.setMute(true);
            player.setAutoPlay(true);
            mediaView.setMediaPlayer(player);
            mediaView.setPreserveRatio(false);
            mediaView.fitWidthProperty().bind(view.widthProperty());
            mediaView.fitHeightProperty().bind(view.heightProperty());
            mediaView.setVisible(true);
        } catch (Exception e) {
            mediaView.setVisible(false);
        }
    }

    private void stopMedia() {
        if (player != null) {
            player.stop();
            player.dispose();
            player = null;
        }
        mediaView.setMediaPlayer(null);
        mediaView.setVisible(false);
    }

    /** Watches the Windows wallpaper and reloads it when the system changes it. */
    private void startWatcher() {
        if (watcher != null) {
            return;
        }
        watcher = new Timeline(new KeyFrame(Duration.seconds(2), event -> pollWallpaper()));
        watcher.setCycleCount(Timeline.INDEFINITE);
        watcher.play();
    }

    private void stopWatcher() {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
    }

    private void pollWallpaper() {
        Path source = registryWallpaper();
        if (source == null) {
            return;
        }
        long stamp;
        try {
            stamp = Files.getLastModifiedTime(source).toMillis() ^ Files.size(source);
        } catch (IOException e) {
            return;
        }
        if (stamp == lastWatchStamp) {
            return;
        }
        lastWatchStamp = stamp;
        try {
            Path dest = Path.of(context.settings.backgroundImage);
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            apply();
        } catch (Exception ignored) {
        }
    }

    public boolean setLocalImage(Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            return false;
        }
        boolean video = isVideo(source);
        if (!video) {
            Image probe = new Image(source.toUri().toString(), false);
            if (probe.isError() || probe.getWidth() <= 0) {
                return false;
            }
        }
        try {
            Path dir = context.dataRoot.resolve("background");
            FileUtil.ensureDir(dir);
            String ext = extension(source);
            Path dest = dir.resolve("custom" + ext);
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            context.settings.backgroundMode = "IMAGE";
            context.settings.backgroundImage = dest.toString();
            context.settings.save();
            lastWatchStamp = -1;
            apply();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public Path syncDesktopWallpaper() throws IOException {
        Path source = registryWallpaper();
        if (source == null) {
            Path fallback = Path.of(System.getenv("APPDATA") == null ? "" : System.getenv("APPDATA"))
                    .resolve("Microsoft/Windows/Themes/TranscodedWallpaper");
            if (Files.isRegularFile(fallback)) {
                source = fallback;
            }
        }
        if (source == null || !Files.isRegularFile(source) || Files.size(source) < 1024) {
            throw new IOException("未找到桌面壁纸文件，请确认 Windows 已设置壁纸");
        }
        Image probe = new Image(source.toUri().toString(), false);
        if (probe.isError() || probe.getWidth() <= 0) {
            throw new IOException("桌面壁纸格式无法识别: " + source);
        }
        Path dir = context.dataRoot.resolve("background");
        FileUtil.ensureDir(dir);
        String ext = extension(source);
        Path dest = dir.resolve("wallpaper" + ext);
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        context.settings.backgroundMode = "WALLPAPER";
        context.settings.backgroundImage = dest.toString();
        context.settings.save();
        lastWatchStamp = Files.getLastModifiedTime(source).toMillis() ^ Files.size(source);
        apply();
        return dest;
    }

    private static Path registryWallpaper() {
        try {
            Process process = new ProcessBuilder("reg", "query", "HKCU\\Control Panel\\Desktop", "/v", "WallPaper")
                    .redirectErrorStream(true).start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    process.getInputStream(), java.nio.charset.Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int idx = line.indexOf("REG_SZ");
                    if (idx > 0) {
                        String value = line.substring(idx + "REG_SZ".length()).trim();
                        if (!value.isBlank()) {
                            Path path = Path.of(value);
                            if (Files.isRegularFile(path)) {
                                return path;
                            }
                        }
                    }
                }
            }
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isVideo(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp4") || name.endsWith(".m4v") || name.endsWith(".webm");
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot > 0 && name.length() - dot <= 5) {
            return name.substring(dot);
        }
        return ".jpg";
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
