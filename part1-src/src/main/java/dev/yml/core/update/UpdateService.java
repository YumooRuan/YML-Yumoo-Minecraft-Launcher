package dev.yml.core.update;

import dev.yml.core.json.Json;
import dev.yml.core.net.Downloader;
import dev.yml.core.net.Http;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;
import dev.yml.core.util.Progress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Launcher self-update: manifest fetch, version compare, download and in-place updater script. */
public final class UpdateService {

    public static final String CURRENT_VERSION = "1.0.0";

    public record Info(String version, String url, String sha256, String notes, String publishedAt) {
    }

    private UpdateService() {
    }

    public static Info fetch(String feedUrl) throws IOException {
        if (feedUrl == null || feedUrl.isBlank()) {
            return null;
        }
        String text = Http.text(feedUrl, 1024 * 1024);
        Map<String, Object> map = Json.obj(Json.parse(text));
        String version = Json.str(map.get("version"));
        String url = Json.str(map.get("url"));
        if (version == null || url == null) {
            throw new IOException("update.json 缺少 version 或 url");
        }
        return new Info(version, url, Json.str(map.get("sha256"), ""),
                Json.str(map.get("notes"), ""), Json.str(map.get("publishedAt"), ""));
    }

    public static boolean isNewer(String remote) {
        return compare(remote, CURRENT_VERSION) > 0;
    }

    public static int compare(String a, String b) {
        String[] pa = (a == null ? "" : a).split("[.\\-+]");
        String[] pb = (b == null ? "" : b).split("[.\\-+]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            String sa = i < pa.length ? pa[i] : "0";
            String sb = i < pb.length ? pb[i] : "0";
            int cmp;
            try {
                cmp = Integer.compare(Integer.parseInt(sa), Integer.parseInt(sb));
            } catch (NumberFormatException e) {
                cmp = sa.compareTo(sb);
            }
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    public static Path download(Info info, Path dir, Progress progress) throws IOException {
        FileUtil.ensureDir(dir);
        Path target = dir.resolve("yml-" + info.version() + ".zip");
        new Downloader(4).downloadAll(List.of(
                Downloader.Item.of("启动器 " + info.version(), info.url(), target, null, 0)), progress);
        if (info.sha256() != null && !info.sha256().isBlank()) {
            String actual = sha256(target);
            if (!actual.equalsIgnoreCase(info.sha256())) {
                Files.deleteIfExists(target);
                throw new IOException("更新包校验失败（SHA-256 不匹配）");
            }
        }
        return target;
    }

    /** Writes a one-shot batch script that replaces the install directory after the app exits. */
    public static Path writeUpdater(Path zip, Path installDir, Path dataDir) throws IOException {
        FileUtil.ensureDir(dataDir);
        Path script = dataDir.resolve("apply-update.bat");
        String content = "@echo off\r\n"
                + "timeout /t 2 /nobreak >nul\r\n"
                + "powershell -NoProfile -ExecutionPolicy Bypass -Command \"Expand-Archive -LiteralPath '"
                + zip.toAbsolutePath() + "' -DestinationPath '" + installDir.toAbsolutePath() + "' -Force\"\r\n"
                + "start \"\" \"" + installDir.resolve("YML.bat").toAbsolutePath() + "\"\r\n"
                + "del \"%~f0\"\r\n";
        Files.write(script, content.getBytes(StandardCharsets.UTF_8));
        return script;
    }

    public static void launchUpdater(Path script) throws IOException {
        new ProcessBuilder("cmd.exe", "/c", "start", "", "/min", script.toString())
                .directory(script.getParent().toFile())
                .start();
    }

    public static String sha256(Path file) {
        try (var in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.warn("计算 SHA-256 失败: %s", e.getMessage());
            return "";
        }
    }
}
