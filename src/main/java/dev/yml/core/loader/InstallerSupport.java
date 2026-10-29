package dev.yml.core.loader;

import dev.yml.core.json.Json;
import dev.yml.core.util.Log;
import dev.yml.core.util.Progress;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Shared helpers for installer jars (Forge / NeoForge). */
final class InstallerSupport {

    private InstallerSupport() {
    }

    /** Forge and NeoForge installers refuse to run without a launcher_profiles.json. */
    static void ensureLauncherProfiles(Path mcRoot) {
        Path file = mcRoot.resolve("launcher_profiles.json");
        if (Files.isRegularFile(file)) {
            return;
        }
        Map<String, Object> root = Json.newObj();
        root.put("profiles", Json.newObj());
        root.put("settings", Json.newObj());
        root.put("version", 3);
        Json.write(file, root);
    }

    static void run(Path javaExe, Path installerJar, Path mcRoot, Progress progress, String label,
                    Consumer<String> lineSink) {
        Path java = javaExe.toAbsolutePath();
        Path jar = installerJar.toAbsolutePath();
        Path target = mcRoot.toAbsolutePath();
        List<String> command = List.of(java.toString(), "-jar", jar.toString(),
                "--installClient", target.toString());
        Log.info("运行 %s 安装器: %s", label, jar);
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(target.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            Deque<String> tail = new ArrayDeque<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                long last = 0;
                while ((line = reader.readLine()) != null) {
                    tail.addLast(line);
                    if (tail.size() > 80) {
                        tail.removeFirst();
                    }
                    if (lineSink != null) {
                        lineSink.accept(line);
                    }
                    long now = System.currentTimeMillis();
                    if (now - last > 400 && progress != null) {
                        progress.note(label + ": " + trim(line));
                        last = now;
                    }
                }
            }
            if (!process.waitFor(15, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new RuntimeException(label + " 安装超时");
            }
            if (process.exitValue() != 0) {
                throw new RuntimeException(label + " 安装器退出码 " + process.exitValue() + "\n"
                        + String.join("\n", tail));
            }
        } catch (IOException e) {
            throw new RuntimeException("无法运行 " + label + " 安装器: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("安装被中断", e);
        }
    }

    private static String trim(String line) {
        String value = line == null ? "" : line.trim();
        return value.length() > 120 ? value.substring(0, 120) + "..." : value;
    }

    /** Finds the version folder the installer just created. */
    static String findInstalledVersion(Path mcRoot, String baseVersion, String marker, long since) {
        Path versionsDir = mcRoot.resolve("versions");
        List<Path> candidates = new ArrayList<>();
        try (var stream = Files.list(versionsDir)) {
            for (Path dir : (Iterable<Path>) stream::iterator) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                String id = dir.getFileName().toString();
                Path json = dir.resolve(id + ".json");
                if (!Files.isRegularFile(json)) {
                    continue;
                }
                if (Files.getLastModifiedTime(json).toMillis() < since - 5000) {
                    continue;
                }
                String text = Files.readString(json, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (!text.contains("\"inheritsfrom\"")) {
                    continue;
                }
                if (!text.contains(baseVersion.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (marker != null && !text.contains(marker.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                candidates.add(dir);
            }
        } catch (IOException e) {
            throw new RuntimeException("无法扫描版本目录: " + e.getMessage(), e);
        }
        return candidates.stream()
                .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                .map(p -> p.getFileName().toString())
                .orElseThrow(() -> new RuntimeException("安装器已结束，但没有找到新的版本目录"));
    }

    /** Minimal XML {@code <version>} extraction for maven-metadata.xml. */
    static List<String> mavenVersions(String xml) {        List<String> versions = new ArrayList<>();
        int index = 0;
        String open = "<version>";
        String close = "</version>";
        while (true) {
            int start = xml.indexOf(open, index);
            if (start < 0) {
                break;
            }
            int end = xml.indexOf(close, start);
            if (end < 0) {
                break;
            }
            versions.add(xml.substring(start + open.length(), end).trim());
            index = end + close.length();
        }
        return versions;
    }

    /**
     * The bundled Forge/NeoForge installer downloader sometimes leaves truncated jars behind.
     * Pre-downloading the libraries from the installer's own manifest avoids those failures.
     */
    static void preseedLibraries(Path installerJar, Path mcRoot, dev.yml.core.net.Mirror.Source source,
                                 Progress progress, String fallbackMaven) throws Exception {
        List<dev.yml.core.net.Downloader.Item> items = new ArrayList<>();
        try (var zip = new java.util.zip.ZipFile(installerJar.toFile())) {
            for (String entryName : new String[]{"version.json", "install_profile.json"}) {
                var entry = zip.getEntry(entryName);
                if (entry == null) {
                    continue;
                }
                String text;
                try (var in = zip.getInputStream(entry)) {
                    text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                Map<String, Object> root = Json.obj(Json.parse(text));
                for (Object libObject : Json.arrOrEmpty(root.get("libraries"))) {
                    Map<String, Object> lib = Json.objOrEmpty(libObject);
                    String name = Json.str(lib.get("name"));
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    // Native-only entries (jinput-platform / twitch-platform) have no base jar.
                    boolean nativeOnly = lib.get("natives") instanceof Map<?, ?>
                            && Json.get(lib, "downloads", "artifact") == null;
                    if (nativeOnly || name.startsWith("net.java.jinput:jinput-platform:")) {
                        continue;
                    }
                    String path = Json.getStr(null, lib, "downloads", "artifact", "path");
                    String url = Json.getStr(null, lib, "downloads", "artifact", "url");
                    String sha1 = Json.getStr(null, lib, "downloads", "artifact", "sha1");
                    long size = Json.lng(Json.get(lib, "downloads", "artifact", "size"), 0);
                    if (path == null) {
                        path = dev.yml.core.game.Library.mavenPath(name);
                    }
                    if (url == null) {
                        String base = Json.str(lib.get("url"), fallbackMaven);
                        url = base.endsWith("/") ? base + path : base + "/" + path;
                    }
                    items.add(dev.yml.core.net.Downloader.Item.of(name,
                            dev.yml.core.net.Mirror.candidates(url, source),
                            mcRoot.resolve("libraries").resolve(path), sha1, size));
                }
            }
        }
        if (!items.isEmpty()) {
            if (progress != null) {
                progress.note("预下载安装器依赖 (" + items.size() + ")");
            }
            new dev.yml.core.net.Downloader(8).downloadAll(items, progress);
        }
    }
}
