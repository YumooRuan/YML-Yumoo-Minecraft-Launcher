package dev.yml.core.game;

import dev.yml.core.json.Json;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scans {@code <minecraft>/versions} for installed versions. */
public final class LocalVersions {

    private static final Pattern VERSION_IN_ID = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?(?:-[a-z0-9-]+)?)");

    public record Installed(String id, Path dir, Path jsonFile, VersionInfo info, LoaderType loader,
                            String baseVersion, long modified, boolean broken, String error) {

        public Path modsDir(Path mcRoot, boolean isolated) {
            Path gameDir = isolated ? dir : mcRoot;
            return gameDir.resolve("mods");
        }

        public Path gameDir(Path mcRoot, boolean isolated) {
            return isolated ? dir : mcRoot;
        }

        public Path clientJar(Path mcRoot) {
            Path versionsDir = mcRoot.resolve("versions");
            String jarName = info != null && info.jar != null ? info.jar : id;
            Path direct = dir.resolve(jarName + ".jar");
            if (Files.isRegularFile(direct)) {
                return direct;
            }
            if (info != null && info.inheritsFrom != null) {
                Path parent = versionsDir.resolve(info.inheritsFrom).resolve(info.inheritsFrom + ".jar");
                if (Files.isRegularFile(parent)) {
                    return parent;
                }
            }
            return null;
        }

        public int modCount(Path mcRoot, boolean isolated) {
            Path mods = modsDir(mcRoot, isolated);
            if (!Files.isDirectory(mods)) {
                return 0;
            }
            try (var stream = Files.list(mods)) {
                return (int) stream.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return n.endsWith(".jar") || n.endsWith(".jar.disabled");
                }).count();
            } catch (Exception e) {
                return 0;
            }
        }
    }

    private LocalVersions() {
    }

    public static List<Installed> scan(Path mcRoot) {
        List<Installed> list = new ArrayList<>();
        Path versionsDir = mcRoot.resolve("versions");
        if (!Files.isDirectory(versionsDir)) {
            return list;
        }
        for (Path dir : FileUtil.dirs(versionsDir)) {
            String id = dir.getFileName().toString();
            Path jsonFile = dir.resolve(id + ".json");
            if (!Files.isRegularFile(jsonFile)) {
                continue;
            }
            try {
                VersionInfo info = VersionInfo.load(jsonFile, versionsDir);
                LoaderType loader = LoaderType.detect(info);
                String base = info.inheritsFrom != null ? info.inheritsFrom : guessBaseVersion(id, info);
                long modified = Files.getLastModifiedTime(jsonFile).toMillis();
                list.add(new Installed(id, dir, jsonFile, info, loader, base, modified, false, null));
            } catch (Exception e) {
                Log.warn("读取版本 %s 失败: %s", id, e.getMessage());
                long modified = FileUtil.size(jsonFile);
                list.add(new Installed(id, dir, jsonFile, null, LoaderType.UNKNOWN, id, modified, true, e.getMessage()));
            }
        }
        list.sort(Comparator.comparing(Installed::modified).reversed());
        return list;
    }

    private static String guessBaseVersion(String id, VersionInfo info) {
        if (info != null && info.assets != null && info.assets.matches("\\d+")) {
            // Modern vanilla version ids equal the game version.
        }
        Matcher matcher = VERSION_IN_ID.matcher(id);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return id;
    }

    public static boolean isVersionDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        String id = dir.getFileName().toString();
        return Files.isRegularFile(dir.resolve(id + ".json"));
    }

    public static VersionInfo loadInfo(Path versionsDir, String id) {
        return VersionInfo.load(versionsDir.resolve(id).resolve(id + ".json"), versionsDir);
    }

    public static String readType(Path jsonFile) {
        try {
            return Json.getStr("release", Json.parse(jsonFile), "type");
        } catch (Exception e) {
            return "release";
        }
    }
}
