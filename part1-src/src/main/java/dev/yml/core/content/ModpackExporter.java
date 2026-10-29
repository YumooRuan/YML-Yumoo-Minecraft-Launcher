package dev.yml.core.content;

import dev.yml.core.game.LocalVersions;
import dev.yml.core.game.VersionInfo;
import dev.yml.core.json.Json;
import dev.yml.core.util.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exports an instance as a HMCL/CurseForge style modpack zip. */
public final class ModpackExporter {

    private static final List<String> INCLUDE = List.of(
            "mods", "config", "defaultconfigs", "resourcepacks", "shaderpacks",
            "options.txt", "servers.dat", "kubejs", "scripts", "datapacks", "patchouli_books");

    private ModpackExporter() {
    }

    public static Path export(LocalVersions.Installed version, Path gameDir, Path outZip,
                              String packName, String packVersion) throws IOException {
        VersionInfo info = version.info();
        String mcVersion = info == null ? version.baseVersion() : info.baseVersion();
        String loader = loaderId(version);
        FileUtil.ensureDir(outZip.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(outZip))) {
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("manifestType", "minecraftModpack");
            manifest.put("manifestVersion", 1);
            manifest.put("name", packName);
            manifest.put("version", packVersion);
            manifest.put("author", "YML");
            manifest.put("files", List.of());
            manifest.put("overrides", "overrides");
            Map<String, Object> minecraft = new LinkedHashMap<>();
            minecraft.put("version", mcVersion);
            if (loader != null) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", loader);
                entry.put("primary", true);
                minecraft.put("modLoaders", List.of(entry));
            }
            manifest.put("minecraft", minecraft);
            writeEntry(zip, "manifest.json", Json.write(manifest, true).getBytes(StandardCharsets.UTF_8));

            for (String name : INCLUDE) {
                Path path = gameDir.resolve(name);
                if (Files.isDirectory(path)) {
                    copyTree(zip, path, "overrides/" + name + "/");
                } else if (Files.isRegularFile(path)) {
                    copyFile(zip, path, "overrides/" + name);
                }
            }
        }
        return outZip;
    }

    private static String loaderId(LocalVersions.Installed version) {
        VersionInfo info = version.info();
        if (info == null) {
            return null;
        }
        for (var library : info.libraries) {
            String name = library.name.toLowerCase(Locale.ROOT);
            if (name.startsWith("net.fabricmc:fabric-loader:")) {
                return "fabric-" + library.version;
            }
            if (name.startsWith("org.quiltmc:quilt-loader:")) {
                return "quilt-" + library.version;
            }
            if (name.startsWith("net.neoforged:neoforge:")) {
                return "neoforge-" + library.version;
            }
            if (name.startsWith("net.minecraftforge:forge:")) {
                return "forge-" + library.version;
            }
        }
        String id = version.id().toLowerCase(Locale.ROOT);
        if (id.contains("forge-")) {
            return "forge-" + id.substring(id.lastIndexOf("forge-") + 6);
        }
        if (id.startsWith("neoforge-")) {
            return "neoforge-" + id.substring("neoforge-".length());
        }
        if (id.startsWith("fabric-loader-")) {
            return "fabric-" + id.substring("fabric-loader-".length(), id.lastIndexOf('-'));
        }
        return null;
    }

    private static void copyTree(ZipOutputStream zip, Path dir, String prefix) throws IOException {
        try (var stream = Files.walk(dir)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String relative = dir.relativize(file).toString().replace('\\', '/');
                copyFile(zip, file, prefix + relative);
            }
        }
    }

    private static void copyFile(ZipOutputStream zip, Path file, String entryName) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = Files.newInputStream(file)) {
            in.transferTo(zip);
        }
        zip.closeEntry();
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }
}
