package dev.yml.core.game;

import dev.yml.core.json.Json;
import dev.yml.core.util.FileUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prepares an instance's game directory before the first launch so players can drop mods,
 * resource packs and shaders in without starting the game once.
 */
public final class InstanceScaffolder {

    public static final List<String> DIRECTORIES = List.of(
            "mods", "config", "defaultconfigs", "resourcepacks", "shaderpacks", "saves",
            "screenshots", "logs", "crash-reports", "datapacks");

    private InstanceScaffolder() {
    }

    /**
     * @param gameDir   actual game directory (version dir when isolated, .minecraft otherwise)
     * @param instanceId version id
     * @param loader    loader name for the marker
     * @param isolated  whether version isolation is enabled
     * @param langCode  Minecraft language code to pre-seed, or null
     */
    public static void ensure(Path gameDir, String instanceId, String loader, boolean isolated,
                              String langCode) {
        if (gameDir == null) {
            return;
        }
        try {
            FileUtil.ensureDir(gameDir);
            for (String name : DIRECTORIES) {
                Files.createDirectories(gameDir.resolve(name));
            }
            Path marker = gameDir.resolve(".yml-instance.json");
            if (isolated && !Files.isRegularFile(marker)) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("launcher", "YML");
                data.put("version", instanceId);
                data.put("loader", loader);
                data.put("isolated", isolated);
                data.put("created", Instant.now().toString());
                data.put("format", 1);
                Json.write(marker, data);
            }
            if (langCode != null && !langCode.isBlank()
                    && !Files.isRegularFile(gameDir.resolve("options.txt"))) {
                GameLanguage.apply(gameDir, langCode);
            }
        } catch (Exception ignored) {
        }
    }
}
