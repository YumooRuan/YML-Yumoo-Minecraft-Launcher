package dev.yml.core.game;

import dev.yml.core.json.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parsed Minecraft version descriptor. Supports {@code inheritsFrom} chains as produced by
 * Forge, NeoForge, Fabric, Quilt and OptiFine installers.
 */
public final class VersionInfo {

    public String id;
    public String type = "release";
    public String mainClass;
    public String assets;
    public String inheritsFrom;
    public String minecraftArguments;
    public String jar;

    public int javaMajor;
    public String javaComponent;

    public String assetIndexId;
    public String assetIndexUrl;
    public String assetIndexSha1;
    public long assetIndexSize;
    public long assetTotalSize;

    public String clientUrl;
    public String clientSha1;
    public long clientSize;

    public String logArgument;
    public String logConfigId;
    public String logConfigUrl;
    public String logConfigSha1;
    public long logConfigSize;

    public String releaseTime;
    public String time;

    public final List<Library> libraries = new ArrayList<>();
    public final List<Object> gameArgs = new ArrayList<>();
    public final List<Object> jvmArgs = new ArrayList<>();

    public Map<String, Object> raw = Json.newObj();

    public static VersionInfo parse(Map<String, Object> map) {
        VersionInfo v = new VersionInfo();
        v.raw = map;
        v.id = Json.str(map.get("id"), "");
        v.type = Json.str(map.get("type"), "release");
        v.mainClass = Json.str(map.get("mainClass"));
        v.assets = Json.str(map.get("assets"));
        v.inheritsFrom = Json.str(map.get("inheritsFrom"));
        v.minecraftArguments = Json.str(map.get("minecraftArguments"));
        v.jar = Json.str(map.get("jar"));
        v.javaMajor = Json.integer(Json.get(map, "javaVersion", "majorVersion"), 0);
        v.javaComponent = Json.str(Json.get(map, "javaVersion", "component"));

        Object assetIndex = map.get("assetIndex");
        if (assetIndex instanceof Map<?, ?> ai) {
            Map<String, Object> index = Json.obj(ai);
            v.assetIndexId = Json.str(index.get("id"));
            v.assetIndexUrl = Json.str(index.get("url"));
            v.assetIndexSha1 = Json.str(index.get("sha1"));
            v.assetIndexSize = Json.lng(index.get("size"), 0);
            v.assetTotalSize = Json.lng(index.get("totalSize"), 0);
        }

        Object client = Json.get(map, "downloads", "client");
        if (client instanceof Map<?, ?> c) {
            Map<String, Object> cm = Json.obj(c);
            v.clientUrl = Json.str(cm.get("url"));
            v.clientSha1 = Json.str(cm.get("sha1"));
            v.clientSize = Json.lng(cm.get("size"), 0);
        }

        v.logArgument = Json.getStr(null, map, "logging", "client", "argument");
        Object logFile = Json.get(map, "logging", "client", "file");
        if (logFile instanceof Map<?, ?> lf) {
            Map<String, Object> fm = Json.obj(lf);
            v.logConfigId = Json.str(fm.get("id"));
            v.logConfigUrl = Json.str(fm.get("url"));
            v.logConfigSha1 = Json.str(fm.get("sha1"));
            v.logConfigSize = Json.lng(fm.get("size"), 0);
        }

        v.libraries.addAll(Library.parseList(map.get("libraries")));

        Object arguments = map.get("arguments");
        if (arguments instanceof Map<?, ?> am) {
            Map<String, Object> args = Json.obj(am);
            v.gameArgs.addAll(Json.arrOrEmpty(args.get("game")));
            v.jvmArgs.addAll(Json.arrOrEmpty(args.get("jvm")));
        }

        v.releaseTime = Json.str(map.get("releaseTime"));
        v.time = Json.str(map.get("time"));
        return v;
    }

    /** Loads a version JSON, following and merging its {@code inheritsFrom} chain. */
    public static VersionInfo load(Path jsonFile, Path versionsDir) {
        Map<String, Object> map = Json.obj(Json.parse(jsonFile));
        VersionInfo info = parse(map);
        if (info.inheritsFrom != null && !info.inheritsFrom.isBlank()) {
            VersionInfo parent = loadParent(info.inheritsFrom, versionsDir);
            info.mergeParent(parent);
        }
        if (info.mainClass == null || info.mainClass.isBlank()) {
            info.mainClass = "net.minecraft.client.main.Main";
        }
        return info;
    }

    private static VersionInfo loadParent(String parentId, Path versionsDir) {
        Path parentDir = versionsDir.resolve(parentId);
        Path parentJson = parentDir.resolve(parentId + ".json");
        if (!Files.isRegularFile(parentJson)) {
            throw new IllegalStateException("缺少父版本 " + parentId + "，请先安装该版本");
        }
        return load(parentJson, versionsDir);
    }

    private void mergeParent(VersionInfo parent) {
        Set<String> keys = new LinkedHashSet<>();
        List<Library> merged = new ArrayList<>(libraries.size() + parent.libraries.size());
        for (Library library : libraries) {
            if (keys.add(library.key())) {
                merged.add(library);
            }
        }
        for (Library library : parent.libraries) {
            if (keys.add(library.key())) {
                merged.add(library);
            }
        }
        libraries.clear();
        libraries.addAll(merged);

        List<Object> mergedGame = new ArrayList<>(parent.gameArgs);
        mergedGame.addAll(gameArgs);
        gameArgs.clear();
        gameArgs.addAll(mergedGame);

        List<Object> mergedJvm = new ArrayList<>(parent.jvmArgs);
        mergedJvm.addAll(jvmArgs);
        jvmArgs.clear();
        jvmArgs.addAll(mergedJvm);

        if (mainClass == null || mainClass.isBlank()) {
            mainClass = parent.mainClass;
        }
        if (assets == null || assets.isBlank()) {
            assets = parent.assets;
        }
        if (minecraftArguments == null) {
            minecraftArguments = parent.minecraftArguments;
        }
        if (jar == null) {
            jar = parent.jar;
        }
        if (javaMajor <= 0) {
            javaMajor = parent.javaMajor;
        }
        if (javaComponent == null) {
            javaComponent = parent.javaComponent;
        }
        if (assetIndexId == null) {
            assetIndexId = parent.assetIndexId;
            assetIndexUrl = parent.assetIndexUrl;
            assetIndexSha1 = parent.assetIndexSha1;
            assetIndexSize = parent.assetIndexSize;
            assetTotalSize = parent.assetTotalSize;
        }
        if (clientUrl == null) {
            clientUrl = parent.clientUrl;
            clientSha1 = parent.clientSha1;
            clientSize = parent.clientSize;
        }
        if (logConfigUrl == null) {
            logArgument = parent.logArgument;
            logConfigId = parent.logConfigId;
            logConfigUrl = parent.logConfigUrl;
            logConfigSha1 = parent.logConfigSha1;
            logConfigSize = parent.logConfigSize;
        }
        if (releaseTime == null) {
            releaseTime = parent.releaseTime;
        }
        if (time == null) {
            time = parent.time;
        }
        if (type == null || "release".equals(type) && parent.type != null && !"release".equals(parent.type)) {
            type = parent.type;
        }
    }

    public boolean hasLegacyArguments() {
        return (gameArgs.isEmpty()) && minecraftArguments != null && !minecraftArguments.isBlank();
    }

    public String rootVersionId() {
        return inheritsFrom == null ? id : inheritsFrom;
    }

    /** Convenience accessor used by the UI: the base Minecraft version this descriptor builds on. */
    public String baseVersion() {
        String base = rootVersionId();
        return base == null ? id : base;
    }
}
