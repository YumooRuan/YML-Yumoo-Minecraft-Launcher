package dev.yml.core.game;

import dev.yml.core.json.Json;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads mod ids/names from installed jars so key bindings can be grouped by mod. */
public final class ModScanner {

    public record ModEntry(String id, String name, String loader, Path jar) {
    }

    private ModScanner() {
    }

    public static List<ModEntry> scan(Path modsDir) {
        List<ModEntry> list = new ArrayList<>();
        if (modsDir == null || !Files.isDirectory(modsDir)) {
            return list;
        }
        try (var stream = Files.list(modsDir)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".jar")) {
                    continue;
                }
                ModEntry entry = read(file);
                if (entry != null) {
                    list.add(entry);
                }
            }
        } catch (Exception e) {
            Log.warn("扫描模组失败: %s", e.getMessage());
        }
        list.sort(java.util.Comparator
                .comparingInt((ModEntry entry) -> loaderRank(entry.loader()))
                .thenComparing(ModEntry::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private static int loaderRank(String loader) {
        return switch (loader == null ? "" : loader) {
            case "FABRIC" -> 0;
            case "FORGE" -> 1;
            case "NEOFORGE" -> 2;
            case "QUILT" -> 3;
            default -> 4;
        };
    }

    public record Meta(String id, String name, String version, String loader) {
    }

    /** Declared dependencies / incompatibilities used for conflict detection. */
    public record Relations(Map<String, String> depends, Map<String, String> breaks,
                            java.util.Set<String> provides) {
        public static Relations empty() {
            return new Relations(Map.of(), Map.of(), java.util.Set.of());
        }
    }

    public static Relations relations(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry fabric = entry(zip, "fabric.mod.json");
            if (fabric != null) {
                try (InputStream in = zip.getInputStream(fabric)) {
                    Map<String, Object> root = Json.obj(Json.parse(new String(in.readAllBytes(),
                            StandardCharsets.UTF_8)));
                    Map<String, String> depends = relationMap(root.get("depends"));
                    Map<String, String> breaks = relationMap(root.get("breaks"));
                    java.util.Set<String> provides = new java.util.LinkedHashSet<>();
                    for (Object value : Json.arrOrEmpty(root.get("provides"))) {
                        if (value instanceof String s) {
                            provides.add(s);
                        }
                    }
                    return new Relations(depends, breaks, provides);
                }
            }
            for (String metaName : new String[]{"META-INF/neoforge.mods.toml", "META-INF/mods.toml"}) {
                ZipEntry toml = entry(zip, metaName);
                if (toml == null) {
                    continue;
                }
                String text;
                try (InputStream in = zip.getInputStream(toml)) {
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                Map<String, String> depends = new java.util.LinkedHashMap<>();
                Map<String, String> breaks = new java.util.LinkedHashMap<>();
                var block = java.util.regex.Pattern.compile(
                        "(?s)\\[\\[dependencies[^\\]]*\\]\\](.*?)(?=\\n\\s*\\[\\[|\\z)").matcher(text);
                while (block.find()) {
                    String body = block.group(1);
                    String modId = firstMatch(body, "modId\\s*=\\s*\"([^\"]+)\"");
                    if (modId == null || modId.isBlank()) {
                        continue;
                    }
                    String range = firstMatch(body, "versionRange\\s*=\\s*\"([^\"]*)\"");
                    String type = firstMatch(body, "type\\s*=\\s*\"([^\"]*)\"");
                    boolean mandatory = "true".equalsIgnoreCase(
                            firstMatch(body, "mandatory\\s*=\\s*\"?([^\"\\s]+)\"?"));
                    if ("incompatible".equalsIgnoreCase(type)) {
                        breaks.put(modId, range == null ? "*" : range);
                    } else if (mandatory && !"forge".equals(modId) && !"neoforge".equals(modId)
                            && !"minecraft".equals(modId)) {
                        depends.put(modId, range == null ? "*" : range);
                    }
                }
                if (!depends.isEmpty() || !breaks.isEmpty()) {
                    return new Relations(depends, breaks, java.util.Set.of());
                }
            }
        } catch (Exception ignored) {
        }
        return Relations.empty();
    }

    private static Map<String, String> relationMap(Object raw) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                if (value instanceof String s) {
                    result.put(key, s);
                } else if (value instanceof java.util.List<?> list && !list.isEmpty()) {
                    result.put(key, String.valueOf(list.get(0)));
                } else {
                    result.put(key, "*");
                }
            }
        }
        return result;
    }

    /** Reads mod metadata; returns null for jars that are not mods (libraries etc.). */
    public static Meta meta(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry fabric = entry(zip, "fabric.mod.json");
            if (fabric != null) {
                try (InputStream in = zip.getInputStream(fabric)) {
                    Map<String, Object> root = Json.obj(Json.parse(new String(in.readAllBytes(),
                            StandardCharsets.UTF_8)));
                    String id = Json.str(root.get("id"));
                    if (id != null) {
                        return new Meta(id, Json.str(root.get("name"), id),
                                Json.str(root.get("version"), ""), "FABRIC");
                    }
                }
            }
            for (String meta : new String[]{"META-INF/neoforge.mods.toml", "META-INF/mods.toml"}) {
                ZipEntry toml = entry(zip, meta);
                if (toml == null) {
                    continue;
                }
                String text;
                try (InputStream in = zip.getInputStream(toml)) {
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                String id = firstMatch(text, "modId\\s*=\\s*\"([^\"]+)\"");
                if (id != null) {
                    String display = firstMatch(text, "displayName\\s*=\\s*\"([^\"]+)\"");
                    String version = firstMatch(text, "(?m)^\\s*version\\s*=\\s*\"([^\"]+)\"");
                    return new Meta(id, display == null ? id : display, version == null ? "" : version,
                            "neoforge.mods.toml".equals(meta) ? "NEOFORGE" : "FORGE");
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Reads a mod icon from the jar (Fabric icon / Forge logoFile / root icon.png), or null. */
    public static byte[] iconBytes(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile(), StandardCharsets.UTF_8)) {
            String icon = null;
            ZipEntry fabric = entry(zip, "fabric.mod.json");
            if (fabric != null) {
                try (InputStream in = zip.getInputStream(fabric)) {
                    Map<String, Object> root = Json.obj(Json.parse(new String(in.readAllBytes(),
                            StandardCharsets.UTF_8)));
                    Object value = root.get("icon");
                    if (value instanceof String s) {
                        icon = s;
                    } else if (value instanceof Map<?, ?> map) {
                        for (String key : new String[]{"128", "64", "32"}) {
                            Object candidate = map.get(key);
                            if (candidate instanceof String s) {
                                icon = s;
                                break;
                            }
                        }
                    }
                }
            }
            if (icon == null) {
                for (String meta : new String[]{"META-INF/neoforge.mods.toml", "META-INF/mods.toml"}) {
                    ZipEntry toml = entry(zip, meta);
                    if (toml == null) {
                        continue;
                    }
                    String text;
                    try (InputStream in = zip.getInputStream(toml)) {
                        text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    icon = firstMatch(text, "logoFile\\s*=\\s*\"([^\"]+)\"");
                    if (icon != null) {
                        break;
                    }
                }
            }
            if (icon == null) {
                for (String candidate : new String[]{"icon.png", "logo.png", "pack.png"}) {
                    if (entry(zip, candidate) != null) {
                        icon = candidate;
                        break;
                    }
                }
            }
            if (icon == null) {
                return null;
            }
            icon = icon.replace('\\', '/');
            ZipEntry entry = entry(zip, icon);
            if (entry == null) {
                entry = entry(zip, icon.toLowerCase(Locale.ROOT));
            }
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                byte[] bytes = in.readAllBytes();
                return bytes.length > 2 * 1024 * 1024 ? null : bytes;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static ModEntry read(Path jar) {        try (ZipFile zip = new ZipFile(jar.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry fabric = entry(zip, "fabric.mod.json");
            if (fabric != null) {
                try (InputStream in = zip.getInputStream(fabric)) {
                    Map<String, Object> root = Json.obj(Json.parse(new String(in.readAllBytes(),
                            StandardCharsets.UTF_8)));
                    String id = Json.str(root.get("id"));
                    if (id != null) {
                        return new ModEntry(id, Json.str(root.get("name"), id), "FABRIC", jar);
                    }
                }
            }
            for (String meta : new String[]{"META-INF/neoforge.mods.toml", "META-INF/mods.toml"}) {
                ZipEntry toml = entry(zip, meta);
                if (toml == null) {
                    continue;
                }
                String text;
                try (InputStream in = zip.getInputStream(toml)) {
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                String id = firstMatch(text, "modId\\s*=\\s*\"([^\"]+)\"");
                String display = firstMatch(text, "displayName\\s*=\\s*\"([^\"]+)\"");
                if (id != null) {
                    return new ModEntry(id, display == null ? id : display,
                            "neoforge.mods.toml".equals(meta) ? "NEOFORGE" : "FORGE", jar);
                }
            }
        } catch (Exception ignored) {
        }
        String file = FileUtil.fileName(jar);
        String base = file.endsWith(".jar") ? file.substring(0, file.length() - 4) : file;
        return new ModEntry(base.toLowerCase(Locale.ROOT), base, "UNKNOWN", jar);
    }

    private static ZipEntry entry(ZipFile zip, String name) {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            entry = zip.getEntry(name.replace('/', '\\'));
        }
        if (entry == null) {
            entry = zip.getEntry(name.toLowerCase(Locale.ROOT));
        }
        return entry;
    }

    private static String firstMatch(String text, String regex) {
        var matcher = java.util.regex.Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
