package dev.yml.core.config;

import dev.yml.core.AppPaths;
import dev.yml.core.auth.Account;
import dev.yml.core.json.Json;
import dev.yml.core.net.Mirror;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Global launcher settings, persisted as {@code <data>/config.json}. */
public final class Settings {

    public transient Path file;

    public String mcRoot = AppPaths.defaultMcRoot().toString();
    public String downloadSource = "AUTO";
    public String javaSource = "ADOPTIUM_API";
    public String curseForgeApiKey = "";
    public String microsoftClientId = "";
    public String updateFeedUrl = "";
    public String skippedUpdateVersion = "";
    public int threads = 16;
    public int defaultMemoryMb = 8192;
    public String defaultJavaPath = "";
    public String language = "zh_CN";
    public boolean syncLanguageToGame;
    public boolean hideAfterLaunch;
    public boolean closeAfterLaunch;
    public boolean isolatedByDefault = true;
    public boolean showGameLog = true;
    public boolean autoAnalyzeCrash = true;
    public boolean noDefaultJvmArgs;
    public boolean noJvmTuning;
    public boolean noJavaCompatCheck;
    public String globalJvmArgs = "";
    public String nativeLibraryPath = "";
    public boolean checkFilesOnLaunch = true;
    public String lastVersionId = "";
    public String lastPlayerName = "Steve";
    public double windowWidth = 1100;
    public double windowHeight = 700;
    public boolean maximized;

    public String backgroundMode = "DEFAULT";
    public String themeMode = "BLACK";
    public String themeColor = "#1b1b22";
    public String backgroundImage = "";
    public double backgroundDim = 0.62;
    public double backgroundBlur = 0;

    public final List<Account> accounts = new ArrayList<>();
    public final List<String> favorites = new ArrayList<>();
    public final List<String> favoriteAnnouncements = new ArrayList<>();
    public String announcementFeed = "";
    public int selectedAccount = 0;

    public static Settings load(Path dataRoot) {
        Settings settings = new Settings();
        settings.file = dataRoot.resolve("config.json");
        if (Files.isRegularFile(settings.file)) {
            try {
                Map<String, Object> map = Json.obj(Json.parse(settings.file));
                settings.mcRoot = Json.str(map.get("mcRoot"), settings.mcRoot);
                settings.downloadSource = Json.str(map.get("downloadSource"), "AUTO");
                settings.javaSource = Json.str(map.get("javaSource"), "ADOPTIUM_API");
                settings.curseForgeApiKey = Json.str(map.get("curseForgeApiKey"), "");
                settings.microsoftClientId = Json.str(map.get("microsoftClientId"), "");
                settings.updateFeedUrl = Json.str(map.get("updateFeedUrl"), "");
                settings.skippedUpdateVersion = Json.str(map.get("skippedUpdateVersion"), "");
                settings.threads = Json.integer(map.get("threads"), 16);
                settings.defaultMemoryMb = Json.integer(map.get("defaultMemoryMb"), 8192);
                settings.defaultJavaPath = Json.str(map.get("defaultJavaPath"), "");
                settings.language = Json.str(map.get("language"), "zh_CN");
                settings.syncLanguageToGame = Json.bool(map.get("syncLanguageToGame"), false);
                settings.hideAfterLaunch = Json.bool(map.get("hideAfterLaunch"), false);
                settings.closeAfterLaunch = Json.bool(map.get("closeAfterLaunch"), false);
                settings.isolatedByDefault = Json.bool(map.get("isolatedByDefault"), true);
                settings.showGameLog = Json.bool(map.get("showGameLog"), true);
                settings.autoAnalyzeCrash = Json.bool(map.get("autoAnalyzeCrash"), true);
        settings.noDefaultJvmArgs = Json.bool(map.get("noDefaultJvmArgs"), false);
        settings.noJvmTuning = Json.bool(map.get("noJvmTuning"), false);
        settings.noJavaCompatCheck = Json.bool(map.get("noJavaCompatCheck"), false);
                settings.globalJvmArgs = Json.str(map.get("globalJvmArgs"), "");
                settings.nativeLibraryPath = Json.str(map.get("nativeLibraryPath"), "");
                settings.checkFilesOnLaunch = Json.bool(map.get("checkFilesOnLaunch"), true);
                settings.lastVersionId = Json.str(map.get("lastVersionId"), "");
                settings.lastPlayerName = Json.str(map.get("lastPlayerName"), "Steve");
                settings.windowWidth = Json.dbl(map.get("windowWidth"), 1180);
                settings.windowHeight = Json.dbl(map.get("windowHeight"), 740);
                settings.maximized = Json.bool(map.get("maximized"), false);
                settings.backgroundMode = Json.str(map.get("backgroundMode"), "DEFAULT");        settings.themeMode = Json.str(map.get("themeMode"), "BLACK");
        settings.themeColor = Json.str(map.get("themeColor"), "#1b1b22");
        settings.announcementFeed = Json.str(map.get("announcementFeed"), "");
        for (Object o : Json.arrOrEmpty(map.get("favoriteAnnouncements"))) {
            if (o != null) {
                settings.favoriteAnnouncements.add(String.valueOf(o));
            }
        }
                settings.backgroundImage = Json.str(map.get("backgroundImage"), "");
                settings.backgroundDim = Json.dbl(map.get("backgroundDim"), 0.55);
                settings.backgroundBlur = Json.dbl(map.get("backgroundBlur"), 0);
                for (Object o : Json.arrOrEmpty(map.get("accounts"))) {
                    Map<String, Object> a = Json.objOrEmpty(o);
                    Account account = new Account();
                    account.name = Json.str(a.get("name"), "Player");
                    String type = Json.str(a.get("type"), "OFFLINE");
                    account.type = switch (type) {
                        case "MICROSOFT" -> Account.Type.MICROSOFT;
                        case "EXTERNAL" -> Account.Type.EXTERNAL;
                        default -> Account.Type.OFFLINE;
                    };
                    account.uuid = Json.str(a.get("uuid"), Account.offlineUuid(account.name));
                    account.accessToken = Json.str(a.get("accessToken"), "0");
                    account.refreshToken = Json.str(a.get("refreshToken"), null);
                    account.apiRoot = Json.str(a.get("apiRoot"), null);
                    account.skinMode = Json.str(a.get("skinMode"), "STEVE");
                    account.skinPath = Json.str(a.get("skinPath"), null);
                    account.expiresAt = Json.lng(a.get("expiresAt"), 0);
                    settings.accounts.add(account);
                }
                settings.selectedAccount = Json.integer(map.get("selectedAccount"), 0);
                for (Object o : Json.arrOrEmpty(map.get("favorites"))) {
                    if (o instanceof String id) {
                        settings.favorites.add(id);
                    }
                }
            } catch (Exception ignored) {
                // Corrupted config: fall back to defaults.
            }
        }
        if (settings.accounts.isEmpty()) {
            settings.accounts.add(Account.offline(settings.lastPlayerName));
        }
        if (settings.selectedAccount < 0 || settings.selectedAccount >= settings.accounts.size()) {
            settings.selectedAccount = 0;
        }
        return settings;
    }

    public void save() {
        Map<String, Object> map = Json.newObj();
        map.put("mcRoot", mcRoot);
        map.put("downloadSource", downloadSource);
        map.put("javaSource", javaSource);
        map.put("curseForgeApiKey", curseForgeApiKey);
        map.put("microsoftClientId", microsoftClientId);
        map.put("updateFeedUrl", updateFeedUrl);
        map.put("skippedUpdateVersion", skippedUpdateVersion);
        map.put("threads", threads);
        map.put("defaultMemoryMb", defaultMemoryMb);
        map.put("defaultJavaPath", defaultJavaPath);
        map.put("language", language);
        map.put("syncLanguageToGame", syncLanguageToGame);
        map.put("hideAfterLaunch", hideAfterLaunch);
        map.put("closeAfterLaunch", closeAfterLaunch);
        map.put("isolatedByDefault", isolatedByDefault);
        map.put("showGameLog", showGameLog);
        map.put("autoAnalyzeCrash", autoAnalyzeCrash);
        map.put("noDefaultJvmArgs", noDefaultJvmArgs);
        map.put("noJvmTuning", noJvmTuning);
        map.put("noJavaCompatCheck", noJavaCompatCheck);
        map.put("globalJvmArgs", globalJvmArgs);
        map.put("nativeLibraryPath", nativeLibraryPath);
        map.put("checkFilesOnLaunch", checkFilesOnLaunch);
        map.put("lastVersionId", lastVersionId);
        map.put("lastPlayerName", lastPlayerName);
        map.put("windowWidth", windowWidth);
        map.put("windowHeight", windowHeight);
        map.put("maximized", maximized);
        map.put("backgroundMode", backgroundMode);
        map.put("announcementFeed", announcementFeed);
        map.put("favoriteAnnouncements", new ArrayList<Object>(favoriteAnnouncements));
        map.put("themeMode", themeMode);
        map.put("themeColor", themeColor);
        map.put("backgroundImage", backgroundImage);
        map.put("backgroundDim", backgroundDim);
        map.put("backgroundBlur", backgroundBlur);
        List<Object> accountList = Json.newArr();
        for (Account account : accounts) {
            Map<String, Object> a = Json.newObj();
            a.put("name", account.name);
            a.put("type", account.type.name());
            a.put("uuid", account.uuid);
            a.put("accessToken", account.accessToken);
            if (account.refreshToken != null) {
                a.put("refreshToken", account.refreshToken);
            }
            if (account.apiRoot != null) {
                a.put("apiRoot", account.apiRoot);
            }
            a.put("skinMode", account.skinMode == null ? "STEVE" : account.skinMode);
            if (account.skinPath != null) {
                a.put("skinPath", account.skinPath);
            }
            if (account.expiresAt > 0) {
                a.put("expiresAt", account.expiresAt);
            }
            accountList.add(a);
        }
        map.put("accounts", accountList);
        map.put("favorites", new ArrayList<Object>(favorites));
        map.put("selectedAccount", selectedAccount);
        Json.write(file, map);
    }

    public Mirror.Source source() {
        try {
            return Mirror.Source.valueOf(downloadSource);
        } catch (IllegalArgumentException e) {
            return Mirror.Source.AUTO;
        }
    }

    public Path mcPath() {
        return Path.of(mcRoot);
    }

    public Account account() {
        if (accounts.isEmpty()) {
            accounts.add(Account.offline(lastPlayerName));
        }
        if (selectedAccount < 0 || selectedAccount >= accounts.size()) {
            selectedAccount = 0;
        }
        return accounts.get(selectedAccount);
    }
}
