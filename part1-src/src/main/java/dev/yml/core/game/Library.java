package dev.yml.core.game;

import dev.yml.core.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A dependency entry from a version JSON's {@code libraries} array. */
public final class Library {

    public static final String DEFAULT_MAVEN = "https://libraries.minecraft.net/";

    private final Map<String, Object> raw;
    public final String name;
    public final String groupId;
    public final String artifactId;
    public final String version;
    public final String classifier;
    public final String baseUrl;
    public final List<Rule> rules;
    public final Map<String, String> natives;
    public final List<String> extractExclude;

    private Library(Map<String, Object> raw) {
        this.raw = raw;
        this.name = Json.str(raw.get("name"), "");
        String[] parts = name.split(":");
        this.groupId = parts.length > 0 ? parts[0] : "";
        this.artifactId = parts.length > 1 ? parts[1] : "";
        this.version = parts.length > 2 ? parts[2] : "";
        this.classifier = parts.length > 3 ? parts[3] : null;
        String url = Json.str(raw.get("url"));
        this.baseUrl = url == null || url.isBlank() ? DEFAULT_MAVEN : url;
        this.rules = Rule.parseList(raw.get("rules"));
        Map<String, String> nativeMap = new LinkedHashMap<>();
        if (raw.get("natives") instanceof Map<?, ?> m) {
            for (var e : Json.obj(m).entrySet()) {
                nativeMap.put(e.getKey(), Json.str(e.getValue(), ""));
            }
        }
        this.natives = nativeMap;
        List<String> excludes = new ArrayList<>();
        Object extract = Json.get(raw, "extract", "exclude");
        if (extract instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) {
                    excludes.add(s);
                }
            }
        }
        this.extractExclude = excludes;
    }

    public static Library parse(Object json) {
        return new Library(Json.obj(json));
    }

    public static List<Library> parseList(Object jsonArray) {
        List<Library> list = new ArrayList<>();
        if (jsonArray instanceof List<?> array) {
            for (Object o : array) {
                if (o instanceof Map<?, ?>) {
                    list.add(new Library(Json.obj(o)));
                }
            }
        }
        return list;
    }

    public boolean allowed(Map<String, Boolean> features) {
        if (!Rule.allows(rules, features)) {
            return false;
        }
        return matchesArch();
    }

    /**
     * Modern versions list every native build for an OS (x86 / x64 / arm64) with identical
     * rules, so the architecture has to be filtered from the classifier.
     */
    private boolean matchesArch() {
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.contains(":natives-")) {
            return true;
        }
        String arch = dev.yml.core.util.OsInfo.arch();
        if (lower.endsWith("-x86")) {
            return "x86".equals(arch);
        }
        if (lower.endsWith("-arm64") || lower.endsWith("-aarch64")) {
            return "arm64".equals(arch);
        }
        return true;
    }

    /** Identity used when merging parent/child version JSONs (version is intentionally ignored). */
    public String key() {
        return groupId + ":" + artifactId + ":" + (classifier == null ? "" : classifier);
    }

    public String path() {
        String p = Json.getStr(null, raw, "downloads", "artifact", "path");
        return p != null ? p : mavenPath(name);
    }

    public String url() {
        String u = Json.getStr(null, raw, "downloads", "artifact", "url");
        return u != null ? u : join(baseUrl, path());
    }

    public String sha1() {
        return Json.getStr(null, raw, "downloads", "artifact", "sha1");
    }

    public long size() {
        return Json.lng(Json.get(raw, "downloads", "artifact", "size"), 0);
    }

    /** Classifier for the current OS, e.g. {@code natives-windows}. Null for plain libraries. */
    public String nativeClassifier() {
        if (natives.isEmpty()) {
            return null;
        }
        String key = natives.get(dev.yml.core.util.OsInfo.name());
        if (key == null) {
            key = natives.get("windows");
        }
        if (key == null || key.isBlank()) {
            return null;
        }
        if ("x86".equals(dev.yml.core.util.OsInfo.arch())) {
            key = key.replace("${arch}", "32");
        } else {
            key = key.replace("${arch}", "64");
        }
        return key.isBlank() ? null : key;
    }

    public boolean needsExtraction() {
        return nativeClassifier() != null;
    }

    public String nativePath() {
        String classifier = nativeClassifier();
        if (classifier == null) {
            return null;
        }
        Object path = Json.get(raw, "downloads", "classifiers", classifier, "path");
        if (path instanceof String s) {
            return s;
        }
        return mavenPath(groupId + ":" + artifactId + ":" + version + ":" + classifier);
    }

    public String nativeUrl() {
        String classifier = nativeClassifier();
        if (classifier == null) {
            return null;
        }
        String url = Json.getStr(null, raw, "downloads", "classifiers", classifier, "url");
        return url != null ? url : join(baseUrl, nativePath());
    }

    public String nativeSha1() {
        String classifier = nativeClassifier();
        if (classifier == null) {
            return null;
        }
        return Json.getStr(null, raw, "downloads", "classifiers", classifier, "sha1");
    }

    public long nativeSize() {
        String classifier = nativeClassifier();
        if (classifier == null) {
            return 0;
        }
        return Json.lng(Json.get(raw, "downloads", "classifiers", classifier, "size"), 0);
    }

    public boolean hasArtifact() {
        return Json.get(raw, "downloads", "artifact") instanceof Map<?, ?>;
    }

    /** Entries such as jinput-platform/twitch-platform only ship native classifiers. */
    public boolean isNativesOnly() {
        return !natives.isEmpty() && !hasArtifact() && classifier == null;
    }

    public static String mavenPath(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) {
            return name.replace('.', '/') + ".jar";
        }
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    private static String join(String base, String path) {
        if (path == null || path.isBlank()) {
            return base;
        }
        return base.endsWith("/") ? base + path : base + "/" + path;
    }

    @Override
    public String toString() {
        return name;
    }

    public String displayName() {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("fabric-loader")) {
            return "Fabric Loader " + version;
        }
        if (lower.contains("quilt-loader")) {
            return "Quilt Loader " + version;
        }
        return name;
    }
}
