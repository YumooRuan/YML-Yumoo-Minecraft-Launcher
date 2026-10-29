package dev.yml.core.game;

import dev.yml.core.util.FileUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shader pack settings compatibility with PCL2: settings live next to the pack as
 * {@code shaderpacks/<pack name>.txt} (OptiFine / Iris properties format).
 */
public final class ShaderConfigs {

    public record Pack(String name, Path file, Path config) {
        public boolean hasConfig() {
            return config != null && Files.isRegularFile(config);
        }
    }

    private ShaderConfigs() {
    }

    public static List<Pack> scan(Path shaderDir) {
        List<Pack> list = new ArrayList<>();
        if (shaderDir == null || !Files.isDirectory(shaderDir)) {
            return list;
        }
        try (var stream = Files.list(shaderDir)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                String name = file.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".zip") && !lower.endsWith(".jar")) {
                    continue;
                }
                String base = name.substring(0, name.lastIndexOf('.'));
                Path config = shaderDir.resolve(base + ".txt");
                list.add(new Pack(base, file, config));
            }
        } catch (Exception ignored) {
        }
        list.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return list;
    }

    /** Copies an external PCL2 config file to the pack's settings file. */
    public static void importConfig(Path source, Pack pack) throws Exception {
        FileUtil.ensureDir(pack.file().getParent());
        Files.copy(source, pack.config(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public static void exportConfig(Pack pack, Path target) throws Exception {
        Files.copy(pack.config(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public static int countOptions(Path config) {
        if (config == null || !Files.isRegularFile(config)) {
            return 0;
        }
        int count = 0;
        try {
            for (String line : Files.readAllLines(config)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                    count++;
                }
            }
        } catch (Exception ignored) {
        }
        return count;
    }
}
