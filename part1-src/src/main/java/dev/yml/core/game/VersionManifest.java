package dev.yml.core.game;

import dev.yml.core.json.Json;
import dev.yml.core.net.Http;
import dev.yml.core.net.Mirror;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Remote version list from Mojang (optionally through BMCLAPI). */
public final class VersionManifest {

    public static final String OFFICIAL_URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    public record Remote(String id, String type, String url, String releaseTime) {
    }

    public static final class Data {
        public final List<Remote> versions = new ArrayList<>();
        public String latestRelease;
        public String latestSnapshot;
        public boolean fromCache;
        public Instant fetchedAt;
    }

    private VersionManifest() {
    }

    public static Data load(Mirror.Source source, Path cacheFile, boolean forceReload) {
        if (!forceReload && Files.isRegularFile(cacheFile)) {
            try {
                long age = Duration.between(Files.getLastModifiedTime(cacheFile).toInstant(), Instant.now()).toSeconds();
                if (age < 1800) {
                    Data cached = parse(Json.parse(cacheFile));
                    cached.fromCache = true;
                    return cached;
                }
            } catch (Exception e) {
                Log.warn("版本清单缓存不可用: %s", e.getMessage());
            }
        }
        for (String url : Mirror.candidates(OFFICIAL_URL, source)) {
            try {
                String text = Http.text(url, 8 * 1024 * 1024);
                Object json = Json.parse(text);
                Data data = parse(json);
                data.fromCache = false;
                data.fetchedAt = Instant.now();
                FileUtil.writeString(cacheFile, text);
                return data;
            } catch (Exception e) {
                Log.warn("版本清单下载失败 %s: %s", url, e.getMessage());
            }
        }
        if (Files.isRegularFile(cacheFile)) {
            Data cached = parse(Json.parse(cacheFile));
            cached.fromCache = true;
            return cached;
        }
        throw new RuntimeException("无法获取 Minecraft 版本清单，请检查网络或切换下载源");
    }

    public static Data parse(Object json) {
        Data data = new Data();
        Map<String, Object> map = Json.obj(json);
        Object latest = map.get("latest");
        if (latest instanceof Map<?, ?> m) {
            data.latestRelease = Json.str(Json.obj(m).get("release"));
            data.latestSnapshot = Json.str(Json.obj(m).get("snapshot"));
        }
        for (Object entry : Json.arrOrEmpty(map.get("versions"))) {
            Map<String, Object> v = Json.objOrEmpty(entry);
            String id = Json.str(v.get("id"));
            String type = Json.str(v.get("type"), "release");
            String url = Json.str(v.get("url"));
            if (id != null && url != null) {
                data.versions.add(new Remote(id, type, url, Json.str(v.get("releaseTime"), "")));
            }
        }
        return data;
    }
}
