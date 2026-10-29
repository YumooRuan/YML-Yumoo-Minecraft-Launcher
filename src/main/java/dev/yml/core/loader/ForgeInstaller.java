package dev.yml.core.loader;

import dev.yml.core.net.Downloader;
import dev.yml.core.net.Http;
import dev.yml.core.net.Mirror;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;
import dev.yml.core.util.Progress;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Downloads and runs the official Forge installer. */
public final class ForgeInstaller {

    public static final String MAVEN = "https://maven.minecraftforge.net";
    public static final String METADATA = MAVEN + "/net/minecraftforge/forge/maven-metadata.xml";

    public record ForgeVersion(String mcVersion, String forgeVersion, String full) {
        public String installerUrl() {
            return MAVEN + "/net/minecraftforge/forge/" + full + "/forge-" + full + "-installer.jar";
        }
    }

    private ForgeInstaller() {
    }

    public static List<ForgeVersion> versions(String mcVersion, Mirror.Source source) {
        for (String url : Mirror.candidates(METADATA, source)) {
            try {
                String xml = Http.text(url, 4 * 1024 * 1024);
                List<ForgeVersion> out = new ArrayList<>();
                for (String version : InstallerSupport.mavenVersions(xml)) {
                    if (version.startsWith(mcVersion + "-")) {
                        String forge = version.substring(mcVersion.length() + 1);
                        if (forge.matches("\\d[\\w.\\-]*")) {
                            out.add(new ForgeVersion(mcVersion, forge, version));
                        }
                    }
                }
                out.sort(Comparator.comparing(ForgeVersion::forgeVersion, ForgeInstaller::compareVersions));
                if (!out.isEmpty()) {
                    return out;
                }
                Log.warn("Forge 元数据中没有 %s 的版本 (%s)", mcVersion, url);
            } catch (Exception e) {
                Log.warn("获取 Forge 版本失败 %s: %s", url, e.getMessage());
            }
        }
        return List.of();
    }

    public static String install(ForgeVersion version, Path mcRoot, Path javaExe, Path cacheDir,
                                 Mirror.Source source, Progress progress, Consumer<String> logLine) {
        Path installer = cacheDir.resolve("forge-" + version.full() + "-installer.jar");
        List<String> urls = Mirror.candidates(version.installerUrl(), source);
        if (progress != null) {
            progress.note("下载 Forge 安装器");
        }
        new Downloader(4).downloadAll(List.of(
                Downloader.Item.of("Forge 安装器", urls, installer, null, 0)), progress);
        FileUtil.ensureDir(mcRoot);
        InstallerSupport.ensureLauncherProfiles(mcRoot);
        try {
            String legacy = installLegacy(installer, mcRoot, progress);
            if (legacy != null) {
                return legacy;
            }
        } catch (Exception e) {
            Log.warn("旧版 Forge 手动安装失败，尝试运行安装器: %s", e.getMessage());
        }
        // The bundled installer downloader occasionally writes truncated jars; seeding the
        // libraries from its own manifest makes installation reproducible.
        try {
            preseedLibraries(installer, mcRoot, source, progress);
        } catch (Exception e) {
            Log.warn("预下载 Forge 依赖失败（继续由安装器处理）: %s", e.getMessage());
        }
        long since = System.currentTimeMillis();
        InstallerSupport.run(javaExe, installer, mcRoot, progress, "Forge", logLine);
        return InstallerSupport.findInstalledVersion(mcRoot, version.mcVersion(), "forge", since);
    }

    /**
     * Legacy Forge installers (1.7.10 - 1.12.2) have no --installClient option.
     * Their install_profile.json contains versionInfo and an embedded universal jar;
     * install the client by writing those directly.
     */
    private static String installLegacy(Path installerJar, Path mcRoot, Progress progress) throws Exception {
        try (var zip = new java.util.zip.ZipFile(installerJar.toFile())) {
            var entry = zip.getEntry("install_profile.json");
            if (entry == null) {
                return null;
            }
            Map<String, Object> root;
            try (var in = zip.getInputStream(entry)) {
                root = dev.yml.core.json.Json.obj(dev.yml.core.json.Json.parse(
                        new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
            }
            Object versionInfoRaw = root.get("versionInfo");
            if (!(versionInfoRaw instanceof Map<?, ?>)) {
                return null;
            }
            Map<String, Object> versionInfo = dev.yml.core.json.Json.obj(versionInfoRaw);
            Map<String, Object> install = dev.yml.core.json.Json.objOrEmpty(root.get("install"));
            String id = dev.yml.core.json.Json.str(versionInfo.get("id"),
                    dev.yml.core.json.Json.str(install.get("target")));
            String filePath = dev.yml.core.json.Json.str(install.get("filePath"));
            String installPath = dev.yml.core.json.Json.str(install.get("path"));
            if (id == null || id.isBlank() || filePath == null || installPath == null) {
                return null;
            }
            var jarEntry = zip.getEntry(filePath);
            if (jarEntry == null) {
                return null;
            }
            if (progress != null) {
                progress.note("旧版 Forge 安装器：手动安装客户端");
            }
            Path libPath = mcRoot.resolve("libraries").resolve(
                    dev.yml.core.game.Library.mavenPath(installPath));
            FileUtil.ensureDir(libPath.getParent());
            java.nio.file.Files.copy(zip.getInputStream(jarEntry), libPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Path versionDir = mcRoot.resolve("versions").resolve(id);
            FileUtil.ensureDir(versionDir);
            java.nio.file.Files.writeString(versionDir.resolve(id + ".json"),
                    dev.yml.core.json.Json.write(versionInfo, true),
                    java.nio.charset.StandardCharsets.UTF_8);
            return id;
        }
    }

    private static void preseedLibraries(Path installerJar, Path mcRoot, Mirror.Source source, Progress progress)
            throws Exception {
        List<Downloader.Item> items = new ArrayList<>();
        try (var zip = new java.util.zip.ZipFile(installerJar.toFile())) {
            for (String entryName : new String[]{"version.json", "install_profile.json"}) {
                var entry = zip.getEntry(entryName);
                if (entry == null) {
                    continue;
                }
                String text;
                try (var in = zip.getInputStream(entry)) {
                    text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                Map<String, Object> root = dev.yml.core.json.Json.obj(dev.yml.core.json.Json.parse(text));
                for (Object libObject : dev.yml.core.json.Json.arrOrEmpty(root.get("libraries"))) {
                    Map<String, Object> lib = dev.yml.core.json.Json.objOrEmpty(libObject);
                    String name = dev.yml.core.json.Json.str(lib.get("name"));
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    // Native-only entries (jinput-platform / twitch-platform) have no base jar.
                    boolean nativeOnly = lib.get("natives") instanceof Map<?, ?>
                            && dev.yml.core.json.Json.get(lib, "downloads", "artifact") == null;
                    if (nativeOnly || name.startsWith("net.java.jinput:jinput-platform:")) {
                        continue;
                    }
                    String path = dev.yml.core.json.Json.getStr(null, lib, "downloads", "artifact", "path");
                    String url = dev.yml.core.json.Json.getStr(null, lib, "downloads", "artifact", "url");
                    String sha1 = dev.yml.core.json.Json.getStr(null, lib, "downloads", "artifact", "sha1");
                    long size = dev.yml.core.json.Json.lng(
                            dev.yml.core.json.Json.get(lib, "downloads", "artifact", "size"), 0);
                    if (path == null) {
                        path = dev.yml.core.game.Library.mavenPath(name);
                    }
                    if (url == null) {
                        String base = dev.yml.core.json.Json.str(lib.get("url"), "https://maven.minecraftforge.net/");
                        url = base.endsWith("/") ? base + path : base + "/" + path;
                    }
                    items.add(Downloader.Item.of(name, Mirror.candidates(url, source),
                            mcRoot.resolve("libraries").resolve(path), sha1, size));
                }
            }
        }
        if (!items.isEmpty()) {
            if (progress != null) {
                progress.note("预下载 Forge 依赖 (" + items.size() + ")");
            }
            new Downloader(8).downloadAll(items, progress);
        }
    }

    public static int compareVersions(String a, String b) {
        String[] pa = a.split("[.\\-]");
        String[] pb = b.split("[.\\-]");
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
}
