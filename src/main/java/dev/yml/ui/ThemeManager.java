package dev.yml.ui;

import dev.yml.core.config.Settings;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Applies the launcher accent color: solid black by default, or the dominant color of the background. */
public final class ThemeManager {

    private ThemeManager() {
    }

    public static void apply(Scene scene, Settings settings) {
        if (scene == null || settings == null) {
            return;
        }
        String mode = settings.themeMode == null ? "BLACK" : settings.themeMode;
        dev.yml.core.util.Log.info("theme apply mode=%s color=%s bg=%s", mode, settings.themeColor, settings.backgroundImage);
        boolean black = "BLACK".equals(mode);
        Color accent;
        if (black) {
            accent = Color.web("#1b1b22");
        } else if ("AUTO".equals(mode)) {
            Color dominant = dominant(settings.backgroundImage);
            accent = dominant == null ? Color.web("#1b1b22") : dominant;
        } else {
            accent = parse(settings.themeColor, Color.web("#1b1b22"));
        }
        Color hover = black ? Color.web("#2a2a33") : accent.interpolate(Color.WHITE, 0.2);
        Color strong = black ? Color.web("#e8e8f0") : ensureVisible(accent);
        Color text = black || luminance(accent) < 0.55 ? Color.WHITE : Color.web("#101014");
        int alpha = black ? 10 : 18;
        String soft = black
                ? "rgba(255, 255, 255, 0.10)"
                : String.format(Locale.ROOT, "rgba(%d, %d, %d, 0.18)",
                (int) Math.round(accent.getRed() * 255),
                (int) Math.round(accent.getGreen() * 255),
                (int) Math.round(accent.getBlue() * 255));
        String css = readTheme();
        if (css == null) {
            css = ".root { -fn-accent: %s; }".formatted(hex(accent));
        }
        css = css.replaceAll("(?m)^\\s*-fn-accent\\s*:[^;]*;", "    -fn-accent: " + hex(accent) + ";")
                .replaceAll("(?m)^\\s*-fn-accent-hover\\s*:[^;]*;",
                        "    -fn-accent-hover: " + hex(hover) + ";")
                .replaceAll("(?m)^\\s*-fn-accent-soft\\s*:[^;]*;",
                        "    -fn-accent-soft: " + soft + ";")
                .replaceAll("(?m)^\\s*-fn-accent-strong\\s*:[^;]*;",
                        "    -fn-accent-strong: " + hex(strong) + ";")
                .replaceAll("(?m)^\\s*-fn-accent-text\\s*:[^;]*;",
                        "    -fn-accent-text: " + hex(text) + ";");
        try {
            Path file = Path.of(System.getProperty("java.io.tmpdir"),
                    "yml-theme-" + Integer.toHexString(css.hashCode()) + ".css");
            Files.writeString(file, css);
            dev.yml.core.util.Log.info("theme css written %s", file);
            String url = file.toUri().toString();
            scene.getStylesheets().removeIf(sheet ->
                    sheet.contains("ui/theme.css") || sheet.contains("yml-theme-"));
            scene.getStylesheets().add(url);
        } catch (Exception e) {
            dev.yml.core.util.Log.warn("theme css failed: %s", e.toString());
        }
    }

    private static String readTheme() {
        try (var in = ThemeManager.class.getResourceAsStream("/ui/theme.css")) {
            return in == null ? null : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** Most common vivid color of an image, or null when it cannot be read. */
    public static Color dominant(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".bmp"))) {
            return null;
        }
        Path file = Path.of(path);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            Image image = new Image(file.toUri().toString(), 64, 64, true, true);
            if (image.isError() || image.getPixelReader() == null) {
                return null;
            }
            Map<Integer, int[]> buckets = new HashMap<>();
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getPixelReader().getArgb(x, y);
                    int alpha = (argb >>> 24) & 0xFF;
                    if (alpha < 200) {
                        continue;
                    }
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    double max = Math.max(r, Math.max(g, b)) / 255.0;
                    double min = Math.min(r, Math.min(g, b)) / 255.0;
                    double lum = luminance(Color.rgb(r, g, b));
                    double saturation = max <= 0 ? 0 : (max - min) / max;
                    if (lum < 0.08 || lum > 0.9 || saturation < 0.18) {
                        continue;
                    }
                    int key = (r >> 4) << 8 | (g >> 4) << 4 | (b >> 4);
                    int[] bucket = buckets.computeIfAbsent(key, k -> new int[4]);
                    bucket[0] += r;
                    bucket[1] += g;
                    bucket[2] += b;
                    bucket[3]++;
                }
            }
            double bestScore = 0;
            Color best = null;
            for (int[] bucket : buckets.values()) {
                int count = bucket[3];
                if (count < 4) {
                    continue;
                }
                int r = bucket[0] / count;
                int g = bucket[1] / count;
                int b = bucket[2] / count;
                Color candidate = Color.rgb(r, g, b);
                double max = Math.max(r, Math.max(g, b)) / 255.0;
                double min = Math.min(r, Math.min(g, b)) / 255.0;
                double saturation = max <= 0 ? 0 : (max - min) / max;
                double score = count * (0.5 + saturation);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    private static Color ensureVisible(Color color) {
        Color result = color;
        int guard = 0;
        while (luminance(result) < 0.55 && guard++ < 8) {
            result = result.interpolate(Color.WHITE, 0.18);
        }
        return result;
    }

    private static double luminance(Color color) {
        return 0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue();
    }

    private static Color parse(String value, Color fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Color.web(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String hex(Color color) {
        return String.format(Locale.ROOT, "#%02x%02x%02x",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }
}
