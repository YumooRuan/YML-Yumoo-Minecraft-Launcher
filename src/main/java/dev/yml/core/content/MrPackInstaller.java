package dev.yml.core.content;

import dev.yml.core.game.VersionInfo;
import dev.yml.core.json.Json;
import dev.yml.core.loader.FabricInstaller;
import dev.yml.core.loader.ForgeInstaller;
import dev.yml.core.loader.NeoForgeInstaller;
import dev.yml.core.net.Downloader;
import dev.yml.core.net.Mirror;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;
import dev.yml.core.util.Progress;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Installs a Modrinth {@code .mrpack} modpack as an isolated instance. */
public final class MrPackInstaller {

    public record Result(String versionId, String name, int files) {
    }

    private MrPackInstaller() {
    }

    public static Result install(Path mrpack, Path mcRoot, Path javaExe, Path cacheDir,
                                 Mirror.Source source, int threads, Progress progress) throws IOException {
        Map<String, Object> index;
        try (ZipFile zip = new ZipFile(mrpack.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zip.getEntry("modrinth.index.json");
            if (entry == null) {
                throw new IOException("整合包缺少 modrinth.index.json");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                index = Json.obj(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }

        String name = Json.str(index.get("name"), "Modpack");
        String mcVersion = Json.getStr("", index, "dependencies", "minecraft");
        if (mcVersion.isBlank()) {
            throw new IOException("整合包未声明 Minecraft 版本");
        }
        String fabric = Json.getStr(null, index, "dependencies", "fabric-loader");
        String forge = Json.getStr(null, index, "dependencies", "forge");
        String neoforge = Json.getStr(null, index, "dependencies", "neoforge");

        progress.note("安装整合包所需加载器");
        String loaderId;
        if (fabric != null) {
            loaderId = FabricInstaller.install(mcVersion, fabric, mcRoot, source, threads, progress);
        } else if (neoforge != null) {
            if (javaExe == null) {
                throw new IOException("安装 NeoForge 需要 Java");
            }
            String full = neoforge.startsWith(mcVersion + "-") ? neoforge : neoforge;
            NeoForgeInstaller.NeoVersion version = new NeoForgeInstaller.NeoVersion(mcVersion, full);
            loaderId = NeoForgeInstaller.install(version, mcRoot, javaExe, cacheDir, source, progress, null);
        } else if (forge != null) {
            if (javaExe == null) {
                throw new IOException("安装 Forge 需要 Java");
            }
            String full = forge.startsWith(mcVersion + "-") ? forge : mcVersion + "-" + forge;
            ForgeInstaller.ForgeVersion version = new ForgeInstaller.ForgeVersion(mcVersion,
                    full.substring(mcVersion.length() + 1), full);
            loaderId = ForgeInstaller.install(version, mcRoot, javaExe, cacheDir, source, progress, null);
        } else {
            loaderId = mcVersion;
        }

        String packId = uniqueId(mcRoot, sanitize(name) + "-" + mcVersion);
        Path packDir = mcRoot.resolve("versions").resolve(packId);
        FileUtil.ensureDir(packDir);

        Map<String, Object> profile = Json.newObj();
        profile.put("id", packId);
        profile.put("inheritsFrom", loaderId);
        profile.put("type", "release");
        profile.put("time", java.time.Instant.now().toString());
        profile.put("releaseTime", java.time.Instant.now().toString());
        Json.write(packDir.resolve(packId + ".json"), profile);

        // Files listed in the index (downloaded by hash into the instance directory).
        List<Downloader.Item> items = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        for (Object entry : Json.arrOrEmpty(index.get("files"))) {
            Map<String, Object> file = Json.objOrEmpty(entry);
            String path = Json.str(file.get("path"), "");
            if (path.isBlank()) {
                continue;
            }
            Object env = file.get("env");
            if (env instanceof Map<?, ?> envMap && "unsupported".equals(Json.str(Json.obj(envMap).get("client")))) {
                continue;
            }
            String sha1 = Json.getStr(null, file, "hashes", "sha1");
            long size = Json.lng(file.get("fileSize"), 0);
            List<String> urls = new ArrayList<>();
            for (Object u : Json.arrOrEmpty(file.get("downloads"))) {
                if (u instanceof String s) {
                    urls.add(s);
                }
            }
            if (urls.isEmpty()) {
                failures.put(path, "没有下载地址");
                continue;
            }
            Path dest = packDir.resolve(path.replace('/', java.io.File.separatorChar)).normalize();
            if (!dest.startsWith(packDir)) {
                failures.put(path, "路径越界");
                continue;
            }
            items.add(Downloader.Item.of(path, urls, dest, sha1, size));
        }
        progress.note("下载整合包文件 (" + items.size() + ")");
        new Downloader(Math.max(1, threads)).downloadAll(items, progress);

        // Overrides (config / mods / resource packs bundled inside the pack).
        progress.note("解压整合包覆盖文件");
        try (ZipFile zip = new ZipFile(mrpack.toFile(), StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName().replace('\\', '/');
                if (!entryName.startsWith("overrides/") && !entryName.startsWith("client-overrides/")) {
                    continue;
                }
                String relative = entryName.substring(entryName.indexOf('/') + 1);
                if (relative.isBlank()) {
                    continue;
                }
                Path dest = packDir.resolve(relative).normalize();
                if (!dest.startsWith(packDir)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    FileUtil.ensureDir(dest);
                } else {
                    FileUtil.ensureDir(dest.getParent());
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        Log.info("整合包安装完成: %s (%d 个文件, 失败 %d)", packId, items.size(), failures.size());
        return new Result(packId, name, items.size());
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

    /** Reads the pack name without installing (used by the download list). */
    public static String peekName(Path mrpack) {
        try (ZipFile zip = new ZipFile(mrpack.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zip.getEntry("modrinth.index.json");
            if (entry == null) {
                return mrpack.getFileName().toString();
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return Json.str(Json.obj(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)))
                        .get("name"), mrpack.getFileName().toString());
            }
        } catch (Exception e) {
            return mrpack.getFileName().toString();
        }
    }

    /** Resolves the loader id an existing profile would use; kept for diagnostics. */
    public static String describe(VersionInfo info) {
        return info == null ? "" : info.baseVersion();
    }
}
