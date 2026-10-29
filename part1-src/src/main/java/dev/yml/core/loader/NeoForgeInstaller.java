package dev.yml.core.loader;

import dev.yml.core.net.Downloader;
import dev.yml.core.net.Http;
import dev.yml.core.net.Mirror;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;
import dev.yml.core.util.Progress;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Downloads and runs the NeoForge installer.
 *
 * <p>NeoForge versions are numbered {@code <mc-minor>.<mc-patch>.<build>} (for example
 * {@code 21.1.172} for Minecraft 1.21.1, or {@code 26.1.2.124} for 26.1.2).</p>
 */
public final class NeoForgeInstaller {

    public static final String MAVEN = "https://maven.neoforged.net/releases";
    public static final String METADATA = MAVEN + "/net/neoforged/neoforge/maven-metadata.xml";

    public record NeoVersion(String mcVersion, String version) {
        public String installerUrl() {
            return MAVEN + "/net/neoforged/neoforge/" + version
                    + "/neoforge-" + version + "-installer.jar";
        }
    }

    private NeoForgeInstaller() {
    }

    public static List<NeoVersion> versions(String mcVersion, Mirror.Source source) {
        String prefix = prefixFor(mcVersion);
        if (prefix == null) {
            return List.of();
        }
        for (String url : Mirror.candidates(METADATA,
                source == Mirror.Source.OFFICIAL ? Mirror.Source.OFFICIAL : Mirror.Source.BMCLAPI)) {
            try {
                String xml = Http.text(url, 4 * 1024 * 1024);
                List<NeoVersion> out = new ArrayList<>();
                for (String version : InstallerSupport.mavenVersions(xml)) {
                    if (version.equals(prefix) || version.startsWith(prefix + ".")) {
                        out.add(new NeoVersion(mcVersion, version));
                    }
                }
                out.sort(Comparator.comparing(NeoVersion::version));
                if (!out.isEmpty()) {
                    return out;
                }
            } catch (Exception e) {
                Log.warn("获取 NeoForge 版本失败 %s: %s", url, e.getMessage());
            }
        }
        return List.of();
    }

    /** 1.21.1 -&gt; 21.1, 26.1.2 -&gt; 26.1.2. */
    static String prefixFor(String mcVersion) {
        String version = mcVersion == null ? "" : mcVersion.toLowerCase(Locale.ROOT);
        if (version.startsWith("1.")) {
            String rest = version.substring(2);
            String[] parts = rest.split("\\.");
            if (parts.length >= 2) {
                return parts[0] + "." + parts[1];
            }
            return rest;
        }
        return version;
    }

    public static String install(NeoVersion version, Path mcRoot, Path javaExe, Path cacheDir,
                                 Mirror.Source source, Progress progress, Consumer<String> logLine) {
        Path installer = cacheDir.resolve("neoforge-" + version.version() + "-installer.jar");
        // maven.neoforged.net is frequently unreachable from mainland networks; prefer BMCLAPI.
        Mirror.Source effective = source == Mirror.Source.OFFICIAL ? Mirror.Source.OFFICIAL : Mirror.Source.BMCLAPI;
        List<String> urls = Mirror.candidates(version.installerUrl(), effective);
        if (progress != null) {
            progress.note("下载 NeoForge 安装器");
        }
        new Downloader(4).downloadAll(List.of(
                Downloader.Item.of("NeoForge 安装器", urls, installer, null, 0)), progress);
        FileUtil.ensureDir(mcRoot);
        InstallerSupport.ensureLauncherProfiles(mcRoot);
        try {
            InstallerSupport.preseedLibraries(installer, mcRoot, effective, progress, MAVEN);
        } catch (Exception e) {
            Log.warn("预下载 NeoForge 依赖失败（继续由安装器处理）: %s", e.getMessage());
        }
        long since = System.currentTimeMillis();
        InstallerSupport.run(javaExe, installer, mcRoot, progress, "NeoForge", logLine);
        return InstallerSupport.findInstalledVersion(mcRoot, version.mcVersion(), "neoforge", since);
    }
}
