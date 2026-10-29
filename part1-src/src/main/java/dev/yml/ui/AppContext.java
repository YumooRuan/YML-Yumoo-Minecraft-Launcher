package dev.yml.ui;

import dev.yml.core.AppPaths;
import dev.yml.core.auth.Account;
import dev.yml.core.config.InstanceSettings;
import dev.yml.core.config.Settings;
import dev.yml.core.game.LocalVersions;
import dev.yml.core.java.JavaManager;
import dev.yml.core.net.Mirror;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared application state and services for all pages. */
public final class AppContext {

    public final Path dataRoot;
    public final Settings settings;
    public JavaManager javaManager;
    public final Path keybindPresetFile;
    public final ExecutorService io;

    private List<LocalVersions.Installed> versions = new ArrayList<>();
    private LocalVersions.Installed selected;

    public AppContext(Path dataRoot, Settings settings) {
        this.dataRoot = dataRoot;
        this.settings = settings;
        this.javaManager = new JavaManager(dataRoot, settings.mcPath());
        dev.yml.core.skin.SkinService.init(dataRoot);
        this.keybindPresetFile = dataRoot.resolve("keybinds.json");
        this.io = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "yml-io");
            thread.setDaemon(true);
            return thread;
        });
    }

    public List<LocalVersions.Installed> versions() {
        return versions;
    }

    public void refreshVersions() {
        versions = LocalVersions.scan(settings.mcPath());
        if (selected != null) {
            selected = versions.stream()
                    .filter(v -> v.id().equals(selected.id()))
                    .findFirst().orElse(null);
        }
        if (selected == null && !versions.isEmpty()) {
            String last = settings.lastVersionId;
            selected = versions.stream().filter(v -> v.id().equals(last)).findFirst()
                    .orElse(versions.get(0));
        }
        // Make every installed instance usable right away (mods/config/... folders).
        String langCode = settings.syncLanguageToGame
                ? dev.yml.core.i18n.Language.gameCode(settings.language) : null;
        for (LocalVersions.Installed version : versions) {
            InstanceSettings instance = InstanceSettings.load(version.dir(), version.id(),
                    settings.isolatedByDefault);
            Path dir = instance.isolated ? version.dir() : settings.mcPath();
            dev.yml.core.game.InstanceScaffolder.ensure(dir, version.id(),
                    version.loader().name(), instance.isolated, langCode);
        }
    }

    public LocalVersions.Installed selected() {
        return selected;
    }

    public void select(LocalVersions.Installed version) {
        this.selected = version;
        if (version != null) {
            settings.lastVersionId = version.id();
        }
    }

    public Account account() {
        return settings.account();
    }

    public Mirror.Source source() {
        return settings.source();
    }

    public InstanceSettings instanceSettings(LocalVersions.Installed installed) {
        return InstanceSettings.load(installed.dir(), installed.id(), settings.isolatedByDefault);
    }

    public Path optionsFile(LocalVersions.Installed installed) {
        InstanceSettings instance = instanceSettings(installed);
        Path gameDir = instance.isolated ? installed.dir() : settings.mcPath();
        if (instance.gameDirOverride != null && !instance.gameDirOverride.isBlank()) {
            gameDir = Path.of(instance.gameDirOverride);
        }
        return gameDir.resolve("options.txt");
    }

    public Path logsDir() {
        return AppPaths.logsDir(dataRoot);
    }

    public boolean isFavorite(String versionId) {
        return versionId != null && settings.favorites.contains(versionId);
    }

    /** Returns true when the version is now a favorite. Favorites are pinned on the home page. */
    public boolean toggleFavorite(String versionId) {
        if (versionId == null) {
            return false;
        }
        if (settings.favorites.remove(versionId)) {
            settings.save();
            return false;
        }
        settings.favorites.add(versionId);
        settings.save();
        return true;
    }

    /** Switches the Minecraft root and rebuilds Java discovery for the new directory. */
    public void changeMcRoot(Path root) {
        settings.mcRoot = root.toString();
        settings.save();
        javaManager = new JavaManager(dataRoot, root);
        selected = null;
        refreshVersions();
    }
}
