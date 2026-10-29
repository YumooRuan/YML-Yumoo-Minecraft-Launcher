package dev.yml.core.update;

import dev.yml.core.json.Json;
import dev.yml.core.util.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Admin-side release packaging: zip the dist folder and write update.json. */
public final class ReleaseBuilder {

    private ReleaseBuilder() {
    }

    public static Path zip(Path distDir, Path outZip) throws IOException {
        if (!Files.isDirectory(distDir)) {
            throw new IOException("找不到发行目录: " + distDir);
        }
        FileUtil.ensureDir(outZip.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(outZip))) {
            try (var stream = Files.walk(distDir)) {
                for (Path path : (Iterable<Path>) stream::iterator) {
                    if (!Files.isRegularFile(path)) {
                        continue;
                    }
                    String name = distDir.relativize(path).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(name));
                    try (InputStream in = Files.newInputStream(path)) {
                        in.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
        }
        return outZip;
    }

    public static Map<String, Object> manifest(String version, String downloadUrl, Path zip, String notes) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("version", version);
        map.put("url", downloadUrl);
        map.put("sha256", updateSha256(zip));
        map.put("notes", notes == null ? "" : notes);
        map.put("publishedAt", Instant.now().toString());
        return map;
    }

    public static Path writeManifest(Path outJson, Map<String, Object> manifest) {
        FileUtil.writeString(outJson, Json.write(manifest, true));
        return outJson;
    }

    private static String updateSha256(Path zip) {
        return dev.yml.core.update.UpdateService.sha256(zip);
    }

    public static String template(String version, String baseUrl) {
        return "{\n  \"version\": \"" + version + "\",\n  \"url\": \"" + baseUrl + "/yml-" + version
                + ".zip\",\n  \"sha256\": \"<自动生成>\",\n  \"notes\": \"更新内容\",\n  \"publishedAt\": \""
                + Instant.now() + "\"\n}\n";
    }

    public static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
