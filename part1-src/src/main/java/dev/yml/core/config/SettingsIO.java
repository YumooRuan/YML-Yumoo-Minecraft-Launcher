package dev.yml.core.config;

import dev.yml.core.json.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exports only settings that differ from defaults to a portable .YLM file, and applies them back.
 */
public final class SettingsIO {

    private SettingsIO() {
    }

    public static void export(Path file, Settings settings) throws Exception {
        Map<String, Object> modified = new LinkedHashMap<>();
        Map<String, Object> current = configurable(settings);
        Map<String, Object> defaults = configurable(new Settings());
        for (var entry : current.entrySet()) {
            if (!Objects.equals(entry.getValue(), defaults.get(entry.getKey()))) {
                modified.put(entry.getKey(), entry.getValue());
            }
        }
        Files.writeString(file, Json.write(modified, true), StandardCharsets.UTF_8);
    }

    public static int apply(Path file, Settings target) throws Exception {
        Map<String, Object> map = Json.obj(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
        int applied = 0;
        for (var entry : map.entrySet()) {
            if (applyOne(target, entry.getKey(), entry.getValue())) {
                applied++;
            }
        }
        return applied;
    }

    private static Map<String, Object> configurable(Settings s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mcRoot", s.mcRoot);
        map.put("downloadSource", s.downloadSource);
        map.put("javaSource", s.javaSource);
        map.put("curseForgeApiKey", s.curseForgeApiKey);
        map.put("microsoftClientId", s.microsoftClientId);
        map.put("updateFeedUrl", s.updateFeedUrl);
        map.put("threads", s.threads);
        map.put("defaultMemoryMb", s.defaultMemoryMb);
        map.put("defaultJavaPath", s.defaultJavaPath);
        map.put("language", s.language);
        map.put("syncLanguageToGame", s.syncLanguageToGame);
        map.put("hideAfterLaunch", s.hideAfterLaunch);
        map.put("closeAfterLaunch", s.closeAfterLaunch);
        map.put("isolatedByDefault", s.isolatedByDefault);
        map.put("showGameLog", s.showGameLog);
        map.put("autoAnalyzeCrash", s.autoAnalyzeCrash);
        map.put("noDefaultJvmArgs", s.noDefaultJvmArgs);
        map.put("noJvmTuning", s.noJvmTuning);
        map.put("noJavaCompatCheck", s.noJavaCompatCheck);
        map.put("globalJvmArgs", s.globalJvmArgs);
        map.put("nativeLibraryPath", s.nativeLibraryPath);
        map.put("checkFilesOnLaunch", s.checkFilesOnLaunch);
        map.put("backgroundMode", s.backgroundMode);
        map.put("themeMode", s.themeMode);
        map.put("themeColor", s.themeColor);
        map.put("backgroundImage", s.backgroundImage);
        map.put("backgroundDim", s.backgroundDim);
        map.put("backgroundBlur", s.backgroundBlur);
        map.put("announcementFeed", s.announcementFeed);
        map.put("favorites", new ArrayList<Object>(s.favorites));
        map.put("favoriteAnnouncements", new ArrayList<Object>(s.favoriteAnnouncements));
        return map;
    }

    private static boolean applyOne(Settings s, String key, Object value) {
        switch (key) {
            case "mcRoot" -> s.mcRoot = Json.str(value, s.mcRoot);
            case "downloadSource" -> s.downloadSource = Json.str(value, s.downloadSource);
            case "javaSource" -> s.javaSource = Json.str(value, s.javaSource);
            case "curseForgeApiKey" -> s.curseForgeApiKey = Json.str(value, s.curseForgeApiKey);
            case "microsoftClientId" -> s.microsoftClientId = Json.str(value, s.microsoftClientId);
            case "updateFeedUrl" -> s.updateFeedUrl = Json.str(value, s.updateFeedUrl);
            case "threads" -> s.threads = Json.integer(value, s.threads);
            case "defaultMemoryMb" -> s.defaultMemoryMb = Json.integer(value, s.defaultMemoryMb);
            case "defaultJavaPath" -> s.defaultJavaPath = Json.str(value, s.defaultJavaPath);
            case "language" -> s.language = Json.str(value, s.language);
            case "syncLanguageToGame" -> s.syncLanguageToGame = Json.bool(value, s.syncLanguageToGame);
            case "hideAfterLaunch" -> s.hideAfterLaunch = Json.bool(value, s.hideAfterLaunch);
            case "closeAfterLaunch" -> s.closeAfterLaunch = Json.bool(value, s.closeAfterLaunch);
            case "isolatedByDefault" -> s.isolatedByDefault = Json.bool(value, s.isolatedByDefault);
            case "showGameLog" -> s.showGameLog = Json.bool(value, s.showGameLog);
            case "autoAnalyzeCrash" -> s.autoAnalyzeCrash = Json.bool(value, s.autoAnalyzeCrash);
            case "noDefaultJvmArgs" -> s.noDefaultJvmArgs = Json.bool(value, s.noDefaultJvmArgs);
            case "noJvmTuning" -> s.noJvmTuning = Json.bool(value, s.noJvmTuning);
            case "noJavaCompatCheck" -> s.noJavaCompatCheck = Json.bool(value, s.noJavaCompatCheck);
            case "globalJvmArgs" -> s.globalJvmArgs = Json.str(value, s.globalJvmArgs);
            case "nativeLibraryPath" -> s.nativeLibraryPath = Json.str(value, s.nativeLibraryPath);
            case "checkFilesOnLaunch" -> s.checkFilesOnLaunch = Json.bool(value, s.checkFilesOnLaunch);
            case "backgroundMode" -> s.backgroundMode = Json.str(value, s.backgroundMode);
            case "themeMode" -> s.themeMode = Json.str(value, s.themeMode);
            case "themeColor" -> s.themeColor = Json.str(value, s.themeColor);
            case "backgroundImage" -> s.backgroundImage = Json.str(value, s.backgroundImage);
            case "backgroundDim" -> s.backgroundDim = Json.dbl(value, s.backgroundDim);
            case "backgroundBlur" -> s.backgroundBlur = Json.dbl(value, s.backgroundBlur);
            case "announcementFeed" -> s.announcementFeed = Json.str(value, s.announcementFeed);
            case "favorites" -> {
                s.favorites.clear();
                for (Object item : Json.arrOrEmpty(value)) {
                    s.favorites.add(String.valueOf(item));
                }
            }
            case "favoriteAnnouncements" -> {
                s.favoriteAnnouncements.clear();
                for (Object item : Json.arrOrEmpty(value)) {
                    s.favoriteAnnouncements.add(String.valueOf(item));
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
