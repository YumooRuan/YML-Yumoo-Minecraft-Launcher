package dev.yml.core.game;

import dev.yml.core.net.Mirror;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Everything needed to start one game process. */
public final class LaunchOptions {

    public Path mcRoot;
    public String versionId;
    public Path javaExecutable;

    public int memoryMb = 4096;
    public int minMemoryMb = 512;
    public boolean addDefaultJvmArgs = true;
    public boolean addOptimizedJvmArgs = true;
    public final List<String> globalJvmArgs = new ArrayList<>();
    public String nativeLibraryPath = "";
    public final List<String> extraJvmArgs = new ArrayList<>();
    public final List<String> extraGameArgs = new ArrayList<>();

    public int width = 854;
    public int height = 480;
    public boolean fullscreen;
    public boolean isolated = true;
    public boolean demo;

    public String username = "Player";
    public String uuid = "";
    public String accessToken = "0";
    public String userType = "legacy";

    public String launcherName = "Yumoo Minecraft Launcher";
    public String launcherVersion = "1.0.0";
    public Mirror.Source source = Mirror.Source.AUTO;
    public int threads = 8;

    public Path versionDir() {
        return mcRoot.resolve("versions").resolve(versionId);
    }

    public Path gameDir() {
        return isolated ? versionDir() : mcRoot;
    }

    public static LaunchOptions of(Path mcRoot, String versionId) {
        LaunchOptions options = new LaunchOptions();
        options.mcRoot = mcRoot;
        options.versionId = versionId;
        return options;
    }
}
