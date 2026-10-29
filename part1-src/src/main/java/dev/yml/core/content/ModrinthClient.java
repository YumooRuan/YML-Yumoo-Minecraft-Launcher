package dev.yml.core.content;

import dev.yml.core.json.Json;
import dev.yml.core.net.Http;
import dev.yml.core.net.Mirror;
import dev.yml.core.util.Log;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Minimal Modrinth v2 client (search / versions / files). */
public final class ModrinthClient {

    public static final String API = "https://api.modrinth.com/v2";
    public static final String USER_AGENT = "YML/1.0.0 (github.com/yml-launcher)";

    public record Hit(String projectId, String slug, String title, String description, String projectType,
                      long downloads, String iconUrl, String author, List<String> versions) {
    }

    public record FileEntry(String url, String filename, String sha1, long size) {
    }

    public record Version(String id, String name, String versionNumber, List<String> gameVersions,
                          List<String> loaders, List<FileEntry> files, String datePublished) {
    }

    private ModrinthClient() {
    }

    public static List<Hit> search(String query, String projectType, String gameVersion, String loader, int limit) {
        StringBuilder facets = new StringBuilder("[[\"project_type:").append(projectType).append("\"]");
        if (gameVersion != null && !gameVersion.isBlank()) {
            facets.append(",[\"versions:").append(gameVersion).append("\"]");
        }
        if (loader != null && !loader.isBlank()) {
            facets.append(",[\"categories:").append(loader).append("\"]");
        }
        facets.append("]");
        String url = API + "/search?query=" + encode(query) + "&limit=" + limit
                + "&facets=" + encode(facets.toString()) + "&index=relevance";
        try {
            String text = Http.text(url, 4 * 1024 * 1024);
            Map<String, Object> root = Json.obj(Json.parse(text));
            List<Hit> hits = new ArrayList<>();
            for (Object entry : Json.arrOrEmpty(root.get("hits"))) {
                Map<String, Object> map = Json.objOrEmpty(entry);
                List<String> versions = new ArrayList<>();
                for (Object version : Json.arrOrEmpty(map.get("versions"))) {
                    if (version instanceof String s) {
                        versions.add(s);
                    }
                }
                hits.add(new Hit(
                        Json.str(map.get("project_id"), ""),
                        Json.str(map.get("slug"), ""),
                        Json.str(map.get("title"), ""),
                        Json.str(map.get("description"), ""),
                        Json.str(map.get("project_type"), projectType),
                        Json.lng(map.get("downloads"), 0),
                        Json.str(map.get("icon_url"), ""),
                        Json.str(map.get("author"), ""),
                        versions));
            }
            return hits;
        } catch (Exception e) {
            Log.warn("Modrinth 搜索失败: %s", e.getMessage());
            return List.of();
        }
    }

    public static List<Version> versions(String projectId, String gameVersion, String loader) {
        StringBuilder url = new StringBuilder(API).append("/project/").append(encode(projectId)).append("/version");
        boolean first = true;
        if (gameVersion != null && !gameVersion.isBlank()) {
            url.append(first ? '?' : '&').append("game_versions=")
                    .append(encode("[\"" + gameVersion + "\"]"));
            first = false;
        }
        if (loader != null && !loader.isBlank()) {
            url.append(first ? '?' : '&').append("loaders=").append(encode("[\"" + loader + "\"]"));
        }
        try {
            String text = Http.text(url.toString(), 8 * 1024 * 1024);
            List<Version> versions = new ArrayList<>();
            for (Object entry : Json.arrOrEmpty(Json.parse(text))) {
                Map<String, Object> map = Json.objOrEmpty(entry);
                List<String> gameVersions = new ArrayList<>();
                for (Object gv : Json.arrOrEmpty(map.get("game_versions"))) {
                    if (gv instanceof String s) {
                        gameVersions.add(s);
                    }
                }
                List<String> loaders = new ArrayList<>();
                for (Object l : Json.arrOrEmpty(map.get("loaders"))) {
                    if (l instanceof String s) {
                        loaders.add(s);
                    }
                }
                List<FileEntry> files = new ArrayList<>();
                for (Object f : Json.arrOrEmpty(map.get("files"))) {
                    Map<String, Object> file = Json.objOrEmpty(f);
                    String fileUrl = Json.str(file.get("url"));
                    String filename = Json.str(file.get("filename"));
                    String sha1 = Json.getStr(null, file, "hashes", "sha1");
                    long size = Json.lng(file.get("size"), 0);
                    if (fileUrl != null && filename != null) {
                        files.add(new FileEntry(fileUrl, filename, sha1, size));
                    }
                }
                versions.add(new Version(
                        Json.str(map.get("id"), ""),
                        Json.str(map.get("name"), ""),
                        Json.str(map.get("version_number"), ""),
                        gameVersions, loaders, files,
                        Json.str(map.get("date_published"), "")));
            }
            return versions;
        } catch (Exception e) {
            Log.warn("Modrinth 版本获取失败: %s", e.getMessage());
            return List.of();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public static String downloadUrl(Version version) {
        if (version.files().isEmpty()) {
            return null;
        }
        return version.files().get(0).url();
    }

    public static FileEntry primaryFile(Version version) {
        return version.files().isEmpty() ? null : version.files().get(0);
    }

    public static List<String> candidates(FileEntry file) {
        return Mirror.candidates(file.url(), Mirror.Source.OFFICIAL);
    }

    /** Reverse lookup of an installed jar (SHA-1) to its Modrinth version. */
    public static VersionLookup versionFromHash(String sha1) {
        if (sha1 == null || sha1.isBlank()) {
            return null;
        }
        try {
            String text = Http.text(API + "/version_file/" + sha1 + "?algorithm=sha1", 2 * 1024 * 1024);
            Map<String, Object> map = Json.obj(Json.parse(text));
            return new VersionLookup(
                    Json.str(map.get("project_id"), ""),
                    Json.str(map.get("id"), ""),
                    Json.str(map.get("version_number"), ""));
        } catch (Exception e) {
            return null;
        }
    }

    public record VersionLookup(String projectId, String versionId, String versionNumber) {
    }

    public static String encodeFacet(String value) throws IOException {
        return encode(value);
    }
}
