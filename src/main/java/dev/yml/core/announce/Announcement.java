package dev.yml.core.announce;

import dev.yml.core.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** One published launcher announcement. */
public record Announcement(String id, String title, String content, String time, boolean pinned) {

    public static Announcement create(String title, String content, boolean pinned) {
        String id = Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString((int) (Math.random() * 1296), 36);
        return new Announcement(id, title, content, Instant.now().toString(), pinned);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("title", title);
        map.put("content", content);
        map.put("time", time);
        map.put("pinned", pinned);
        return map;
    }

    public static Announcement fromJson(Map<String, Object> map) {
        return new Announcement(
                Json.str(map.get("id"), Long.toString(System.currentTimeMillis(), 36)),
                Json.str(map.get("title"), "未命名公告"),
                Json.str(map.get("content"), ""),
                Json.str(map.get("time"), Instant.now().toString()),
                Json.bool(map.get("pinned"), false));
    }
}
