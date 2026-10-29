package dev.yml.core.content;

import dev.yml.core.json.Json;
import dev.yml.core.util.Log;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CurseForge v1 client used for world/map downloads.
 *
 * <p>CurseForge requires a free API key per application; the user can paste one into settings.
 * Search works for {@code classId=17} (worlds) and {@code classId=4471} (modpacks).</p>
 */
public final class CurseForgeClient {

    public static final String API = "https://api.curseforge.com/v1";
    public static final String MIRROR = "https://mod.mcimirror.top/curseforge/v1";
    public static final int GAME_MINECRAFT = 432;
    public static final int CLASS_WORLDS = 17;
    public static final int CLASS_MODPACKS = 4471;

    public record Hit(long id, String name, String summary, String iconUrl, long downloads, String websiteUrl) {
    }

    public record CfFile(long id, String displayName, String fileName, String downloadUrl, long size) {
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private CurseForgeClient() {
    }

    public static boolean hasKey(String apiKey) {
        return apiKey != null && !apiKey.isBlank();
    }

    public static List<Hit> searchWorlds(String apiKey, String query, int limit) {
        String url = API + "/mods/search?gameId=" + GAME_MINECRAFT + "&classId=" + CLASS_WORLDS
                + "&searchFilter=" + encode(query) + "&pageSize=" + limit
                + "&sortField=6&sortOrder=desc";
        try {
            String text = get(url, apiKey);
            Map<String, Object> root = Json.obj(Json.parse(text));
            List<Hit> hits = new ArrayList<>();
            for (Object entry : Json.arrOrEmpty(root.get("data"))) {
                Map<String, Object> map = Json.objOrEmpty(entry);
                hits.add(new Hit(
                        Json.lng(map.get("id"), 0),
                        Json.str(map.get("name"), ""),
                        Json.str(map.get("summary"), ""),
                        Json.getStr("", map, "logo", "url"),
                        Json.lng(map.get("downloadCount"), 0),
                        Json.getStr("", map, "links", "websiteUrl")));
            }
            return hits;
        } catch (Exception e) {
            Log.warn("CurseForge 搜索失败: %s", e.getMessage());
            return List.of();
        }
    }

    public static CfFile latestWorldFile(String apiKey, long modId) {
        try {
            String text = get(API + "/mods/" + modId + "/files?pageSize=10", apiKey);
            Map<String, Object> root = Json.obj(Json.parse(text));
            for (Object entry : Json.arrOrEmpty(root.get("data"))) {
                Map<String, Object> file = Json.objOrEmpty(entry);
                String downloadUrl = Json.str(file.get("downloadUrl"));
                long fileId = Json.lng(file.get("id"), 0);
                String fileName = Json.str(file.get("fileName"), "world.zip");
                if (downloadUrl == null || downloadUrl.isBlank()) {
                    downloadUrl = resolveDownloadUrl(apiKey, modId, fileId);
                }
                if (downloadUrl == null || downloadUrl.isBlank()) {
                    downloadUrl = cdnUrl(fileId, fileName);
                }
                if (downloadUrl == null) {
                    continue;
                }
                return new CfFile(fileId, Json.str(file.get("displayName"), ""),
                        fileName, downloadUrl,
                        Json.lng(file.get("fileLength"), 0));
            }
        } catch (Exception e) {
            Log.warn("CurseForge 文件获取失败: %s", e.getMessage());
        }
        return null;
    }

    /** CurseForge CDN direct URL, used when the mirror does not expose download-url. */
    private static String cdnUrl(long fileId, String fileName) {
        if (fileId <= 0 || fileName == null || fileName.isBlank()) {
            return null;
        }
        String encoded = encode(fileName).replace("+", "%20");
        return "https://edge.forgecdn.net/files/" + (fileId / 1000) + "/" + (fileId % 1000)
                + "/" + encoded;
    }

    private static String resolveDownloadUrl(String apiKey, long modId, long fileId) {
        try {
            String text = get(API + "/mods/" + modId + "/files/" + fileId + "/download-url", apiKey);
            return Json.str(Json.obj(Json.parse(text)).get("data"));
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(String url, String apiKey) throws Exception {
        boolean hasKey = hasKey(apiKey);
        String target = hasKey ? url : url.replace(API, MIRROR);
        try {
            return request(target, hasKey ? apiKey : null);
        } catch (Exception e) {
            if (hasKey && url.startsWith(API)) {
                // Official API failed; fall back to the HMCL-style mirror (no user key needed).
                return request(url.replace(API, MIRROR), null);
            }
            throw e;
        }
    }

    private static String request(String url, String apiKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", ModrinthClient.USER_AGENT)
                .header("Accept", "application/json")
                .GET();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("x-api-key", apiKey);
        }
        HttpResponse<byte[]> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) {
            throw new java.io.IOException("HTTP " + response.statusCode());
        }
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
