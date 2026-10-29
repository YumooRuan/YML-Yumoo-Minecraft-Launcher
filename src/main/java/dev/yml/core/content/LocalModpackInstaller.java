package dev.yml.core.content;

import dev.yml.core.game.GameLauncher;
import dev.yml.core.game.VersionManifest;
import dev.yml.core.json.Json;
import dev.yml.core.loader.FabricInstaller;
import dev.yml.core.loader.ForgeInstaller;
import dev.yml.core.loader.NeoForgeInstaller;
import dev.yml.core.net.Mirror;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;
import dev.yml.core.util.Progress;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Imports local modpacks: Modrinth {@code .mrpack} or a YML/HMCL style zip with
 * {@code manifest.json} and an {@code overrides/} folder.
 */
public final class LocalModpackInstaller {

    private LocalModpackInstaller() {
    }

    public static String install(Path archive, Path mcRoot, Path javaExe, Path cacheDir,
                                 Mirror.Source source, int threads, Progress progress) throws IOException {
        String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mrpack")) {
            return MrPackInstaller.install(archive, mcRoot, javaExe, cacheDir, source, threads, progress).versionId();
        }
        Map<String, Object> manifest = readManifest(archive);
        if (manifest == null) {
            throw new IOException("这个 zip 没有 manifest.json，无法识别为整合包");
        }
        String packName = Json.str(manifest.get("name"), "导入整合包");
        String mcVersion = Json.getStr("", manifest, "minecraft", "version");
        if (mcVersion.isBlank()) {
            throw new IOException("manifest.json 缺少 minecraft.version");
        }
        String loader = null;
        for (Object entry : Json.arrOrEmpty(Json.get(manifest, "minecraft", "modLoaders"))) {
            Map<String, Object> loaders = Json.objOrEmpty(entry);
            String id = Json.str(loaders.get("id"));
            if (id != null && !id.isBlank()) {
                loader = id;
                break;
            }
        }

        progress.note("检查原版 " + mcVersion);
        ensureVanilla(mcVersion, mcRoot, source, threads, progress);

        String loaderVersionId = mcVersion;
        if (loader != null) {
            progress.note("安装加载器 " + loader);
            if (loader.startsWith("fabric-")) {
                loaderVersionId = FabricInstaller.install(mcVersion, loader.substring("fabric-".length()),
                        mcRoot, source, threads, progress);
            } else if (loader.startsWith("neoforge-") && javaExe != null) {
                String version = loader.substring("neoforge-".length());
                loaderVersionId = NeoForgeInstaller.install(
                        new NeoForgeInstaller.NeoVersion(mcVersion, version), mcRoot, javaExe, cacheDir,
                        source, progress, null);
            } else if (loader.startsWith("forge-") && javaExe != null) {
                String version = loader.substring("forge-".length());
                String full = version.startsWith(mcVersion + "-") ? version : mcVersion + "-" + version;
                loaderVersionId = ForgeInstaller.install(
                        new ForgeInstaller.ForgeVersion(mcVersion, full.substring(mcVersion.length() + 1), full),
                        mcRoot, javaExe, cacheDir, source, progress, null);
            } else {
                Log.warn("无法自动安装加载器 %s，将按原版导入", loader);
            }
        }

        String packId = uniqueId(mcRoot, sanitize(packName) + "-" + mcVersion);
        Path packDir = mcRoot.resolve("versions").resolve(packId);
        FileUtil.ensureDir(packDir);
        Map<String, Object> profile = Json.newObj();
        profile.put("id", packId);
        profile.put("inheritsFrom", loaderVersionId);
        profile.put("type", "release");
        profile.put("time", java.time.Instant.now().toString());
        profile.put("releaseTime", java.time.Instant.now().toString());
        Json.write(packDir.resolve(packId + ".json"), profile);

        progress.note("解压整合包内容");
        extractOverrides(archive, packDir);
        return packId;
    }

    private static Map<String, Object> readManifest(Path archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return Json.obj(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
    }

    private static void ensureVanilla(String mcVersion, Path mcRoot, Mirror.Source source, int threads,
                                      Progress progress) {
        Path json = mcRoot.resolve("versions").resolve(mcVersion).resolve(mcVersion + ".json");
        if (Files.isRegularFile(json)) {
            return;
        }
        VersionManifest.Data data = VersionManifest.load(source,
                mcRoot.resolve(".yml-cache").resolve("version_manifest_v2.json"), false);
        String url = null;
        for (VersionManifest.Remote remote : data.versions) {
            if (remote.id().equals(mcVersion)) {
                url = remote.url();
                break;
            }
        }
        if (url == null) {
            throw new RuntimeException("找不到 Minecraft " + mcVersion + " 的下载信息");
        }
        GameLauncher.installVanilla(mcVersion, url, mcRoot, source, threads, progress);
    }

    private static void extractOverrides(Path archive, Path packDir) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            boolean hasOverrides = false;
            var probe = zip.entries();
            while (probe.hasMoreElements()) {
                if (probe.nextElement().getName().replace('\\', '/').startsWith("overrides/")) {
                    hasOverrides = true;
                    break;
                }
            }
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory()) {
                    continue;
                }
                String relative;
                if (hasOverrides) {
                    if (!name.startsWith("overrides/")) {
                        continue;
                    }
                    relative = name.substring("overrides/".length());
                } else {
                    if (name.equals("manifest.json") || name.equals("modlist.html")) {
                        continue;
                    }
                    relative = name;
                }
                if (relative.isBlank()) {
                    continue;
                }
                Path dest = packDir.resolve(relative).normalize();
                if (!dest.startsWith(packDir)) {
                    continue;
                }
                FileUtil.ensureDir(dest.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String sanitize(String name) {
        String value = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fa5._-]+", "-")
                .replaceAll("-+", "-");
        if (value.isBlank()) {
            value = "modpack";
        }
        return value.length() > 40 ? value.substring(0, 40) : value;
    }

    private static String uniqueId(Path mcRoot, String base) {
        Path versions = mcRoot.resolve("versions");
        String id = base;
        int counter = 2;
        while (Files.exists(versions.resolve(id))) {
            id = base + "-" + counter++;
        }
        return id;
    }
}
