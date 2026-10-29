package dev.yml.core.config;

import dev.yml.core.json.Json;
import dev.yml.core.util.FileUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Per-version launch settings, stored in {@code <version>/.nebula/instance.json}. */
public final class InstanceSettings {

    public transient Path file;
    public transient String versionId = "";

    public int memoryMb = -1;
    public int minMemoryMb = -1;
    public String javaPath = "";
    public boolean isolated = true;
    public String gameDirOverride = "";
    public int width = 854;
    public int height = 480;
    public boolean fullscreen;
    public final List<String> jvmArgs = new ArrayList<>();
    public final List<String> gameArgs = new ArrayList<>();

    public static InstanceSettings load(Path versionDir, String versionId, boolean defaultIsolated) {
        InstanceSettings settings = new InstanceSettings();
        settings.versionId = versionId;
        settings.file = versionDir.resolve(".nebula").resolve("instance.json");
        settings.isolated = defaultIsolated;
        if (Files.isRegularFile(settings.file)) {
            try {
                Map<String, Object> map = Json.obj(Json.parse(settings.file));
                settings.memoryMb = Json.integer(map.get("memoryMb"), -1);
                settings.minMemoryMb = Json.integer(map.get("minMemoryMb"), -1);
                settings.javaPath = Json.str(map.get("javaPath"), "");
                settings.isolated = Json.bool(map.get("isolated"), defaultIsolated);
                settings.gameDirOverride = Json.str(map.get("gameDirOverride"), "");
                settings.width = Json.integer(map.get("width"), 854);
                settings.height = Json.integer(map.get("height"), 480);
                settings.fullscreen = Json.bool(map.get("fullscreen"), false);
                for (Object o : Json.arrOrEmpty(map.get("jvmArgs"))) {
                    if (o instanceof String s) {
                        settings.jvmArgs.add(s);
                    }
                }
                for (Object o : Json.arrOrEmpty(map.get("gameArgs"))) {
                    if (o instanceof String s) {
                        settings.gameArgs.add(s);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return settings;
    }

    public void save() {
        FileUtil.ensureDir(file.getParent());
        Map<String, Object> map = Json.newObj();
        map.put("memoryMb", memoryMb);
        map.put("minMemoryMb", minMemoryMb);
        map.put("javaPath", javaPath);
        map.put("isolated", isolated);
        map.put("gameDirOverride", gameDirOverride);
        map.put("width", width);
        map.put("height", height);
        map.put("fullscreen", fullscreen);
        map.put("jvmArgs", new ArrayList<Object>(jvmArgs));
        map.put("gameArgs", new ArrayList<Object>(gameArgs));
        Json.write(file, map);
    }

    public boolean hasIsolatedOverride() {
        return Files.isRegularFile(file);
    }
}
