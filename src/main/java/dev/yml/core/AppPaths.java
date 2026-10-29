package dev.yml.core;

import dev.yml.core.json.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Default locations of launcher data and the Minecraft installation. */
public final class AppPaths {

    public static final String DATA_FOLDER = "YM Launcher";
    public static final String PATHS_FILE = "launcher-paths.json";

    private AppPaths() {
    }

    /** Directory containing YML.exe (jpackage) or yml-launcher.jar / YML.bat. */
    public static Path launcherDir() {
        String override = System.getProperty("yml.launcher.dir");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            Path parent = Path.of(appPath).getParent();
            if (parent != null) {
                return parent;
            }
        }
        try {
            Path location = Path.of(AppPaths.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                return location.getParent();
            }
            return location;
        } catch (Exception ignored) {
        }
        return Path.of(System.getProperty("user.dir"));
    }

    public static Path portableDataRoot() {
        return launcherDir().resolve(DATA_FOLDER);
    }

    public static Path portableMcRoot() {
        return launcherDir().resolve(".minecraft");
    }

    public static Path pathsFile() {
        return launcherDir().resolve(PATHS_FILE);
    }

    public static Path defaultDataRoot() {
        String env = System.getenv("YML_HOME");
        if (env == null || env.isBlank()) {
            env = System.getenv("NEBULA_HOME");
        }
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        Path pointed = readPointer("dataRoot");
        if (pointed != null && Files.isDirectory(pointed)) {
            return pointed;
        }
        if (Boolean.getBoolean("yml.portable")) {
            return portableDataRoot();
        }
        if (Files.isRegularFile(portableDataRoot().resolve("config.json"))
                || Files.isDirectory(portableMcRoot())) {
            return portableDataRoot();
        }
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            Path legacy = Path.of(appData, "YML");
            if (Files.isRegularFile(legacy.resolve("config.json"))) {
                return legacy;
            }
        }
        return portableDataRoot();
    }

    /** Copies config/keybinds/background from the pre-rename data directory once. */
    public static void migrateLegacy(Path newRoot) {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            return;
        }
        Path legacy = Path.of(appData, "NebulaLauncher");
        if (!Files.isDirectory(legacy) || Files.exists(newRoot.resolve("config.json"))) {
            return;
        }
        try {
            Files.createDirectories(newRoot);
            Path config = legacy.resolve("config.json");
            if (Files.isRegularFile(config)) {
                Files.copy(config, newRoot.resolve("config.json"), StandardCopyOption.REPLACE_EXISTING);
            }
            Path keybinds = legacy.resolve("keybinds.json");
            if (Files.isRegularFile(keybinds)) {
                Files.copy(keybinds, newRoot.resolve("keybinds.json"), StandardCopyOption.REPLACE_EXISTING);
            }
            Path background = legacy.resolve("background");
            if (Files.isDirectory(background)) {
                Path target = newRoot.resolve("background");
                Files.createDirectories(target);
                try (var stream = Files.list(background)) {
                    for (Path file : (Iterable<Path>) stream::iterator) {
                        if (Files.isRegularFile(file)) {
                            Files.copy(file, target.resolve(file.getFileName()),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static Path defaultMcRoot() {
        return defaultMcRoot(defaultDataRoot());
    }

    public static Path defaultMcRoot(Path dataRoot) {
        Path pointed = readPointer("mcRoot");
        if (pointed != null) {
            return pointed;
        }
        Path portable = portableMcRoot();
        if (Boolean.getBoolean("yml.portable")) {
            return portable;
        }
        if (dataRoot != null && dataRoot.normalize().startsWith(launcherDir().normalize())) {
            return portable;
        }
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, ".minecraft");
        }
        return Path.of(System.getProperty("user.home"), ".minecraft");
    }

    /** Remembers the selected data / game folders next to the launcher (portable pointer). */
    public static void writePointer(Path dataRoot, Path mcRoot) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("dataRoot", dataRoot.toString());
            map.put("mcRoot", mcRoot.toString());
            Files.writeString(pathsFile(), Json.write(map, true));
        } catch (Exception ignored) {
        }
    }

    private static Path readPointer(String key) {
        try {
            Path file = pathsFile();
            if (!Files.isRegularFile(file)) {
                return null;
            }
            Map<String, Object> map = Json.obj(Json.parse(Files.readString(file)));
            String value = Json.str(map.get(key));
            return value == null || value.isBlank() ? null : Path.of(value);
        } catch (Exception e) {
            return null;
        }
    }

    public static Path logsDir(Path dataRoot) {
        return dataRoot.resolve("logs");
    }
}
