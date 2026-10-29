package dev.yml.core.skin;

import dev.yml.core.auth.Account;
import dev.yml.core.json.Json;
import dev.yml.core.net.Http;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

/** Resolves player skins: bundled Steve/Alex, premium lookup, or a local PNG. */
public final class SkinService {

    public enum Mode {
        STEVE, ALEX, PREMIUM, LOCAL
    }

    private static Path dataRoot;
    private static final Object LOCK = new Object();

    private SkinService() {
    }

    public static void init(Path root) {
        dataRoot = root.resolve("skins");
        FileUtil.ensureDir(dataRoot);
    }

    public static Mode modeOf(Account account) {
        if (account == null) {
            return Mode.STEVE;
        }
        String value = account.skinMode == null ? "" : account.skinMode.toUpperCase(Locale.ROOT);
        try {
            return value.isBlank() ? Mode.STEVE : Mode.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Mode.STEVE;
        }
    }

    public static Image skin(Account account) {
        if (account == null) {
            return null;
        }
        return switch (modeOf(account)) {
            case ALEX -> defaultSkin("Alex");
            case PREMIUM -> premiumSkin(account.name);
            case LOCAL -> localSkin(account.skinPath);
            default -> defaultSkin("Steve");
        };
    }

    public static Image head(Account account) {
        Image skin = skin(account);
        if (skin == null) {
            return null;
        }
        int width = (int) skin.getWidth();
        int height = (int) skin.getHeight();
        if (width < 64 || height < 32) {
            return null;
        }
        WritableImage out = new WritableImage(8, 8);
        PixelReader source = skin.getPixelReader();
        PixelWriter target = out.getPixelWriter();
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                target.setArgb(x, y, source.getArgb(8 + x, 8 + y));
            }
        }
        if (height >= 64) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int argb = source.getArgb(40 + x, 8 + y);
                    if ((argb >>> 24) != 0) {
                        target.setArgb(x, y, argb);
                    }
                }
            }
        }
        return out;
    }

    public static Path localCopy(Account account, Path selected) throws Exception {
        FileUtil.ensureDir(dataRoot);
        Path dest = dataRoot.resolve("local-" + sanitize(account.name) + ".png");
        Files.copy(selected, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    public static boolean validSkin(Path file) {
        Image image = load(file);
        return image != null && image.getWidth() >= 64 && image.getHeight() >= 32;
    }

    public static boolean premiumAvailable(String name) {
        return premiumSkinUrl(name) != null;
    }

    private static Image defaultSkin(String variant) {
        Path file = dataRoot == null ? null : dataRoot.resolve(variant.toLowerCase(Locale.ROOT) + ".png");
        synchronized (LOCK) {
            if (file != null && Files.isRegularFile(file)) {
                Image cached = load(file);
                if (cached != null) {
                    return cached;
                }
            }
            try {
                byte[] bytes = Http.bytes("https://mc-heads.net/skin/" + variant, 1024 * 1024);
                if (file != null) {
                    FileUtil.writeBytes(file, bytes);
                }
                return new Image(new java.io.ByteArrayInputStream(bytes));
            } catch (Exception e) {
                Log.warn("默认皮肤下载失败，使用内置样式: %s", e.getMessage());
                return generatedSkin(variant);
            }
        }
    }

    private static Image premiumSkin(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Path cache = dataRoot.resolve("premium-" + sanitize(name) + ".png");
        if (Files.isRegularFile(cache)) {
            Image cached = load(cache);
            if (cached != null) {
                return cached;
            }
        }
        try {
            String url = premiumSkinUrl(name);
            if (url == null) {
                return null;
            }
            byte[] bytes = Http.bytes(url, 2 * 1024 * 1024);
            FileUtil.writeBytes(cache, bytes);
            return new Image(new java.io.ByteArrayInputStream(bytes));
        } catch (Exception e) {
            Log.warn("获取正版皮肤失败: %s", e.getMessage());
            return null;
        }
    }

    private static String premiumSkinUrl(String name) {
        try {
            String profile = Http.text("https://api.mojang.com/users/profiles/minecraft/" + name, 64 * 1024);
            String id = Json.str(Json.obj(Json.parse(profile)).get("id"));
            if (id == null) {
                return null;
            }
            String session = Http.text("https://sessionserver.mojang.com/session/minecraft/profile/" + id,
                    256 * 1024);
            for (Object entry : Json.arrOrEmpty(Json.obj(Json.parse(session)).get("properties"))) {
                Map<String, Object> property = Json.objOrEmpty(entry);
                if (!"textures".equals(Json.str(property.get("name")))) {
                    continue;
                }
                String value = Json.str(property.get("value"));
                if (value == null) {
                    continue;
                }
                String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                return Json.getStr(null, Json.parse(decoded), "textures", "SKIN", "url");
            }
        } catch (Exception e) {
            Log.debug("查询正版皮肤失败 %s: %s", name, e.getMessage());
        }
        return null;
    }

    private static Image localSkin(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Path file = Path.of(path);
        return Files.isRegularFile(file) ? load(file) : null;
    }

    private static Image load(Path file) {
        try {
            Image image = new Image(file.toUri().toString(), false);
            return image.isError() ? null : image;
        } catch (Exception e) {
            return null;
        }
    }

    /** Minimal fallback skins so the preview never breaks offline. */
    private static Image generatedSkin(String variant) {
        WritableImage image = new WritableImage(64, 64);
        PixelWriter writer = image.getPixelWriter();
        boolean alex = "Alex".equalsIgnoreCase(variant);
        int hair = alex ? 0xFFC77B3A : 0xFF3B2A1A;
        int skin = alex ? 0xFFF0C8A0 : 0xFFC8946A;
        int shirt = alex ? 0xFF4F9E4A : 0xFF2F6FB5;
        int pants = alex ? 0xFF6B5A46 : 0xFF3B3B8F;
        int shoes = 0xFF4A4A4A;
        fill(writer, 8, 8, 8, 8, skin);
        fill(writer, 8, 0, 8, 8, hair);
        fill(writer, 0, 8, 8, 8, hair);
        fill(writer, 16, 8, 8, 8, hair);
        fill(writer, 24, 8, 8, 8, hair);
        fill(writer, 20, 20, 8, 12, shirt);
        fill(writer, 44, 20, 4, 12, shirt);
        fill(writer, 36, 52, 4, 12, shirt);
        fill(writer, 4, 20, 4, 12, pants);
        fill(writer, 20, 52, 4, 12, pants);
        fill(writer, 4, 28, 4, 4, shoes);
        fill(writer, 20, 60, 4, 4, shoes);
        return image;
    }

    private static void fill(PixelWriter writer, int x, int y, int w, int h, int argb) {
        for (int iy = y; iy < y + h; iy++) {
            for (int ix = x; ix < x + w; ix++) {
                writer.setArgb(ix, iy, argb);
            }
        }
    }

    private static String sanitize(String name) {
        return name == null ? "player" : name.replaceAll("[^A-Za-z0-9_-]", "_").toLowerCase(Locale.ROOT);
    }
}
