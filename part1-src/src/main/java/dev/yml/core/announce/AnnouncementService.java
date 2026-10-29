package dev.yml.core.announce;

import dev.yml.core.config.Settings;
import dev.yml.core.json.Json;
import dev.yml.core.net.Http;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads and saves announcements; defaults to the online feed, with offline cache fallback. */
public final class AnnouncementService {

    /** Public online announcements feed (readable by everyone, writable by the admin). */
    public static final String DEFAULT_FEED_URL =
            "https://extendsclass.com/api/json-storage/bin/ceaefbc";

    private AnnouncementService() {
    }

    public static String feedUrl(Settings settings) {
        String custom = settings == null ? "" : settings.announcementFeed;
        return custom == null || custom.isBlank() ? DEFAULT_FEED_URL : custom.trim();
    }

    private static volatile String lastStatus = "本地";

    public static String lastStatus() {
        return lastStatus;
    }

    public static List<Announcement> load(Path dataRoot, Settings settings) {
        String feed = feedUrl(settings);
        String text = null;
        lastStatus = "本地";
        try {
            if (feed.startsWith("http://") || feed.startsWith("https://")) {
                try {
                    text = Http.text(feed, 4 * 1024 * 1024);
                    lastStatus = "在线同步成功";
                    Path cache = dataRoot.resolve("cache").resolve("announcements-remote.json");
                    FileUtil.ensureDir(cache.getParent());
                    Files.writeString(cache, text, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    lastStatus = "在线读取失败，已回退缓存";
                    Log.warn("在线公告读取失败，尝试缓存: %s", e.getMessage());
                    Path cache = dataRoot.resolve("cache").resolve("announcements-remote.json");
                    if (Files.isRegularFile(cache)) {
                        text = Files.readString(cache, StandardCharsets.UTF_8);
                    }
                }
            } else if (!feed.isBlank()) {
                Path file = Path.of(feed);
                if (Files.isRegularFile(file)) {
                    text = Files.readString(file, StandardCharsets.UTF_8);
                }
            }
            if (text == null) {
                Path local = dataRoot.resolve("announcements.json");
                if (Files.isRegularFile(local)) {
                    text = Files.readString(local, StandardCharsets.UTF_8);
                }
            }
            if (text == null) {
                Path released = dataRoot.resolve("releases").resolve("announcements.json");
                if (Files.isRegularFile(released)) {
                    text = Files.readString(released, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            Log.warn("读取公告失败: %s", e.getMessage());
        }
        List<Announcement> list = new ArrayList<>();
        if (text != null) {
            try {
                Map<String, Object> root = Json.obj(Json.parse(text));
                for (Object entry : Json.arrOrEmpty(root.get("announcements"))) {
                    list.add(Announcement.fromJson(Json.objOrEmpty(entry)));
                }
            } catch (Exception e) {
                Log.warn("解析公告失败: %s", e.getMessage());
            }
        }
        list.sort(Comparator.comparing(Announcement::pinned).reversed()
                .thenComparing(Comparator.comparing(Announcement::time).reversed()));
        return list;
    }

    /** Saves locally (for backup/offline) and uploads to the online feed when it is HTTP. */
    public static boolean saveLocal(Path dataRoot, List<Announcement> announcements) throws Exception {
        String text = json(announcements);
        FileUtil.ensureDir(dataRoot);
        Files.writeString(dataRoot.resolve("announcements.json"), text, StandardCharsets.UTF_8);
        Path releases = dataRoot.resolve("releases");
        FileUtil.ensureDir(releases);
        Files.writeString(releases.resolve("announcements.json"), text, StandardCharsets.UTF_8);
        return true;
    }

    public static void publishRemote(String feedUrl, List<Announcement> announcements) throws Exception {
        if (feedUrl == null || feedUrl.isBlank()) {
            throw new IllegalArgumentException("公告源为空");
        }
        if (!feedUrl.startsWith("http://") && !feedUrl.startsWith("https://")) {
            return;
        }
        Http.putJson(feedUrl, json(announcements));
    }

    private static String json(List<Announcement> announcements) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", 1);
        root.put("updated", java.time.Instant.now().toString());
        List<Object> items = new ArrayList<>();
        for (Announcement announcement : announcements) {
            items.add(announcement.toJson());
        }
        root.put("announcements", items);
        return Json.write(root, true);
    }
}
