package dev.yml.core.content;

import dev.yml.core.util.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Extracts a downloaded world archive into {@code saves/}. */
public final class WorldInstaller {

    private WorldInstaller() {
    }

    public static Path install(Path archive, Path gameDir, String fallbackName) throws IOException {
        Path saves = gameDir.resolve("saves");
        FileUtil.ensureDir(saves);
        String name = sanitize(fallbackName);
        Path target = unique(saves, name);

        // A world zip either contains level.dat at the root, or a single top level folder.
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            boolean rootLevel = zip.getEntry("level.dat") != null;
            String singleFolder = null;
            if (!rootLevel) {
                String common = null;
                boolean single = true;
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String entryName = entry.getName().replace('\\', '/');
                    int slash = entryName.indexOf('/');
                    if (slash <= 0) {
                        single = false;
                        break;
                    }
                    String top = entryName.substring(0, slash);
                    if (common == null) {
                        common = top;
                    } else if (!common.equals(top)) {
                        single = false;
                        break;
                    }
                }
                if (single) {
                    singleFolder = common;
                }
            }

            if (singleFolder != null) {
                // Strip the wrapper folder and write it as the save directory itself.
                FileUtil.ensureDir(target);
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String entryName = entry.getName().replace('\\', '/');
                    if (!entryName.startsWith(singleFolder + "/")) {
                        continue;
                    }
                    String relative = entryName.substring(singleFolder.length() + 1);
                    if (relative.isBlank()) {
                        continue;
                    }
                    Path dest = target.resolve(relative).normalize();
                    if (!dest.startsWith(target)) {
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
            } else {
                FileUtil.ensureDir(target);
                FileUtil.extractZip(archive, target, entry -> entry.startsWith("__MACOSX"));
                if (!Files.exists(target.resolve("level.dat")) && rootLevel) {
                    // level.dat lived at the archive root; already extracted above.
                }
            }
        }
        return target;
    }

    private static String sanitize(String name) {
        String value = name == null ? "world" : name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (value.isBlank()) {
            value = "world";
        }
        return value.length() > 60 ? value.substring(0, 60) : value;
    }

    private static Path unique(Path saves, String name) {
        Path target = saves.resolve(name);
        int index = 2;
        while (Files.exists(target)) {
            target = saves.resolve(name + "-" + index++);
        }
        return target;
    }

    public static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
