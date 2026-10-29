package dev.yml.core.game;

import dev.yml.core.util.FileUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Writes the Minecraft language code into an instance's options.txt. */
public final class GameLanguage {

    private GameLanguage() {
    }

    public static void apply(Path gameDir, String mcCode) {
        if (gameDir == null || mcCode == null || mcCode.isBlank()) {
            return;
        }
        try {
            FileUtil.ensureDir(gameDir);
            Path options = gameDir.resolve("options.txt");
            List<String> lines = new ArrayList<>();
            boolean replaced = false;
            if (Files.isRegularFile(options)) {
                List<String> existing = Files.readAllLines(options, StandardCharsets.UTF_8);
                for (String line : existing) {
                    if (line.startsWith("lang:")) {
                        if (!replaced) {
                            lines.add("lang:" + mcCode);
                            replaced = true;
                        }
                    } else {
                        lines.add(line);
                    }
                }
            }
            if (!replaced) {
                lines.add("lang:" + mcCode);
            }
            Files.write(options, lines, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
