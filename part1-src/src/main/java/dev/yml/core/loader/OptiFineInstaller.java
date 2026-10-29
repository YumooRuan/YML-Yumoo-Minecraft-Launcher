package dev.yml.core.loader;

import dev.yml.core.json.Json;
import dev.yml.core.net.Downloader;
import dev.yml.core.net.Http;
import dev.yml.core.net.Mirror;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;
import dev.yml.core.util.Progress;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * OptiFine integration.
 *
 * <p>The OptiFine jar is a GUI installer (main class {@code optifine.InstallerFrame}) with no
 * documented command line mode, so the launcher downloads it, runs it once and then detects the
 * version folder it created. The installer writes into the official .minecraft by default.</p>
 */
public final class OptiFineInstaller {

    public static final String MIRROR_LIST = "https://bmclapi2.bangbang93.com/optifine/";

    public record OptiFineVersion(String mcVersion, String type, String patch, String filename, String forge) {

        public String label() {
            return filename.replace("OptiFine_" + mcVersion + "_", "").replace(".jar", "")
                    + (forge == null || forge.isBlank() || "Forge N/A".equals(forge) ? "" : " · " + forge);
        }

        public String downloadUrl() {
            return MIRROR_LIST + mcVersion + "/" + type + "/" + patch;
        }
    }

    private OptiFineInstaller() {
    }

    public static List<OptiFineVersion> versions(String mcVersion, Mirror.Source source) {
        List<OptiFineVersion> out = new ArrayList<>();
        try {
            String text = Http.text(MIRROR_LIST + mcVersion, 2 * 1024 * 1024);
            for (Object entry : Json.arrOrEmpty(Json.parse(text))) {
                Map<String, Object> map = Json.objOrEmpty(entry);
                String filename = Json.str(map.get("filename"));
                String type = Json.str(map.get("type"));
                String patch = Json.str(map.get("patch"));
                if (filename == null || type == null || patch == null || patch.isBlank()) {
                    continue;
                }
                out.add(new OptiFineVersion(Json.str(map.get("mcversion"), mcVersion), type, patch, filename,
                        Json.str(map.get("forge"), "")));
            }
        } catch (Exception e) {
            Log.warn("获取 OptiFine 版本失败: %s", e.getMessage());
        }
        out.sort(Comparator.comparing(OptiFineVersion::filename));
        return out;
    }

    /** Downloads the installer, installs headlessly and falls back to the GUI installer. */
    public static String install(OptiFineVersion version, Path mcRoot, Path javaExe, Path cacheDir,
                                 Progress progress, Consumer<String> logLine) {
        Path jar = cacheDir.resolve(version.filename());
        progress.note("下载 OptiFine 安装器");
        new Downloader(4).downloadAll(List.of(Downloader.Item.of("OptiFine", version.downloadUrl(), jar, null, 0)),
                progress);
        InstallerSupport.ensureLauncherProfiles(mcRoot);
        long since = System.currentTimeMillis();
        progress.note("正在安装 OptiFine（无界面）");
        try {
            installHeadless(jar, mcRoot);
            return findCreatedVersion(mcRoot, version.mcVersion(), since);
        } catch (Throwable e) {
            Log.warn("OptiFine 无界面安装失败，回退到图形安装器: %s", e.getMessage());
        }

        progress.note("请在弹出的 OptiFine 安装器中点击 Install");
        try {
            ProcessBuilder builder = new ProcessBuilder(javaExe.toString(), "-jar", jar.toString());
            builder.directory(mcRoot.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (logLine != null && !line.isBlank()) {
                        logLine.accept(line);
                    }
                }
            }
            if (!process.waitFor(20, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new RuntimeException("OptiFine 安装器超时");
            }
        } catch (IOException e) {
            throw new RuntimeException("无法启动 OptiFine 安装器: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OptiFine 安装被中断", e);
        }
        return findCreatedVersion(mcRoot, version.mcVersion(), since);
    }

    /** Calls {@code optifine.Installer.doInstall(File)} in an isolated class loader. */
    private static void installHeadless(Path jar, Path mcRoot) throws Exception {
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{jar.toUri().toURL()},
                ClassLoader.getSystemClassLoader())) {
            Class<?> installer = Class.forName("optifine.Installer", true, loader);
            var method = installer.getMethod("doInstall", java.io.File.class);
            method.invoke(null, mcRoot.toFile());
        }
    }

    private static String findCreatedVersion(Path mcRoot, String mcVersion, long since) {
        Path versions = mcRoot.resolve("versions");
        List<Path> found = new ArrayList<>();
        try (var stream = Files.list(versions)) {
            for (Path dir : (Iterable<Path>) stream::iterator) {
                String id = dir.getFileName().toString();
                Path json = dir.resolve(id + ".json");
                if (!Files.isRegularFile(json) || Files.getLastModifiedTime(json).toMillis() < since - 5000) {
                    continue;
                }
                String text = Files.readString(json, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (text.contains("optifine") && text.contains(mcVersion.toLowerCase(Locale.ROOT))) {
                    found.add(dir);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("无法扫描 OptiFine 版本目录: " + e.getMessage(), e);
        }
        return found.stream()
                .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                .map(p -> p.getFileName().toString())
                .orElseThrow(() -> new RuntimeException(
                        "未检测到新的 OptiFine 版本。请确认安装器里点击了 Install，且安装目录是 " + mcRoot));
    }

    public static boolean isInstalled(Path mcRoot, OptiFineVersion version) {
        Path versions = mcRoot.resolve("versions");
        if (!Files.isDirectory(versions)) {
            return false;
        }
        try (var stream = Files.list(versions)) {
            return stream.anyMatch(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).contains("optifine"));
        } catch (IOException e) {
            return false;
        }
    }

    static void ensure(Path dir) {
        FileUtil.ensureDir(dir);
    }
}
