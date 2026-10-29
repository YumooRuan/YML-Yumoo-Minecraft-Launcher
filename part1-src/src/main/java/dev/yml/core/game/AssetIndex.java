package dev.yml.core.game;

import dev.yml.core.json.Json;
import dev.yml.core.net.Downloader;
import dev.yml.core.net.Mirror;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Progress;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Asset index ({@code assets/indexes/<id>.json}) helper. */
public final class AssetIndex {

    public final String id;
    public final Map<String, Object> objects;
    public final boolean virtual;
    public final boolean mapToResources;

    private AssetIndex(String id, Map<String, Object> objects, boolean virtual, boolean mapToResources) {
        this.id = id;
        this.objects = objects;
        this.virtual = virtual;
        this.mapToResources = mapToResources;
    }

    public static AssetIndex parse(Object json) {
        Map<String, Object> map = Json.obj(json);
        return new AssetIndex(
                Json.str(map.get("id"), "legacy"),
                Json.objOrEmpty(map.get("objects")),
                Json.bool(map.get("virtual"), false),
                Json.bool(map.get("map_to_resources"), false));
    }

    public static AssetIndex load(Path indexFile) {
        return parse(Json.parse(indexFile));
    }

    /** Builds the download list for all objects plus the index itself. */
    public static List<Downloader.Item> downloads(AssetIndex index, Path assetsDir, Mirror.Source source) {
        List<Downloader.Item> items = new ArrayList<>();
        for (Map.Entry<String, Object> entry : index.objects.entrySet()) {
            Map<String, Object> object = Json.objOrEmpty(entry.getValue());
            String hash = Json.str(object.get("hash"));
            if (hash == null || hash.length() < 2) {
                continue;
            }
            String sub = hash.substring(0, 2);
            Path dest = assetsDir.resolve("objects").resolve(sub).resolve(hash);
            String url = "https://resources.download.minecraft.net/" + sub + "/" + hash;
            items.add(Downloader.Item.of(entry.getKey(), Mirror.candidates(url, source), dest, hash,
                    Json.lng(object.get("size"), 0)));
        }
        return items;
    }

    /** Legacy ({@code virtual}) and pre-1.7 ({@code map_to_resources}) asset layouts need real files. */
    public void materialize(Path assetsDir) {
        if (!virtual && !mapToResources) {
            return;
        }
        Path base = mapToResources ? assetsDir.resolve("resources") : assetsDir.resolve("virtual").resolve(id);
        for (Map.Entry<String, Object> entry : objects.entrySet()) {
            Map<String, Object> object = Json.objOrEmpty(entry.getValue());
            String hash = Json.str(object.get("hash"));
            if (hash == null || hash.length() < 2) {
                continue;
            }
            Path source = assetsDir.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
            Path dest = base.resolve(entry.getKey());
            if (Files.isRegularFile(source) && !Files.isRegularFile(dest)) {
                FileUtil.copy(source, dest);
            }
        }
    }

    public int objectCount() {
        return objects.size();
    }

    /** Marks a subset of assets as complete quickly (used by the progress UI). */
    public static int countPresent(AssetIndex index, Path assetsDir, Progress progress) {
        int present = 0;
        int total = index.objects.size();
        int checked = 0;
        for (Map.Entry<String, Object> entry : index.objects.entrySet()) {
            Map<String, Object> object = Json.objOrEmpty(entry.getValue());
            String hash = Json.str(object.get("hash"));
            if (hash != null && hash.length() >= 2
                    && Files.isRegularFile(assetsDir.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash))) {
                present++;
            }
            checked++;
            if (progress != null && checked % 500 == 0) {
                progress.update(checked, total, "校验资源文件");
            }
        }
        return present;
    }
}
