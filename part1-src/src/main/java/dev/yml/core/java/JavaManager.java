package dev.yml.core.java;

import dev.yml.core.json.Json;
import dev.yml.core.net.Downloader;
import dev.yml.core.net.Http;
import dev.yml.core.net.Mirror;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;
import dev.yml.core.util.OsInfo;
import dev.yml.core.util.Progress;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds Java runtimes installed on the machine and downloads new ones
 * (Mojang's own runtime or Eclipse Temurin) into the launcher's runtime directory.
 */
public final class JavaManager {

    public static final String MOJANG_RUNTIME_INDEX =
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    public record MojangOption(String component, String versionName, String released, String platform,
                               String manifestUrl, String sha1, long size) {
        public String label() {
            return "Java " + JavaRuntime.parseMajor(versionName) + " · Mojang " + component.replace("java-runtime-", "");
        }
    }

    public enum JavaSource {
        ADOPTIUM_API("Adoptium 官方 API"),
        TUNA("清华 TUNA 镜像");

        private final String label;

        JavaSource(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record AdoptiumOption(int major, boolean lts) {
        public String label() {
            return "Java " + major;
        }
    }

    private final Path dataRoot;
    private final Path mcRoot;
    private final Path runtimeDir;
    private final List<JavaRuntime> cache = new CopyOnWriteArrayList<>();

    public JavaManager(Path dataRoot, Path mcRoot) {
        this.dataRoot = dataRoot;
        this.mcRoot = mcRoot;
        this.runtimeDir = dataRoot.resolve("runtime");
    }

    public Path runtimeDir() {
        return runtimeDir;
    }

    // ------------------------------------------------------------- discovery

    public List<JavaRuntime> installed() {
        if (cache.isEmpty()) {
            return refresh();
        }
        return new ArrayList<>(cache);
    }

    public synchronized List<JavaRuntime> refresh() {
        Set<Path> candidates = new LinkedHashSet<>();
        collectLauncherRuntimes(candidates);
        collectMojangRuntimes(candidates);
        collectSystemCandidates(candidates);
        List<JavaRuntime> found = new ArrayList<>();
        AtomicInteger index = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        Object lock = new Object();
        for (Path exe : candidates) {
            Thread thread = new Thread(() -> {
                String source = exe.startsWith(runtimeDir) ? "启动器下载" : "本机";
                JavaRuntime runtime = JavaRuntime.probe(exe, source);
                if (runtime != null) {
                    synchronized (lock) {
                        found.add(runtime);
                    }
                }
                index.incrementAndGet();
            }, "java-probe-" + candidates.size());
            thread.setDaemon(true);
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            try {
                thread.join(20_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        found.sort(Comparator.comparingInt((JavaRuntime runtime) -> runtime.major)
                .thenComparing(runtime -> runtime.version));
        cache.clear();
        cache.addAll(dedupe(found));
        Log.info("发现 Java 运行时 %d 个", cache.size());
        return new ArrayList<>(cache);
    }

    private static List<JavaRuntime> dedupe(List<JavaRuntime> list) {
        Set<String> seen = new LinkedHashSet<>();
        List<JavaRuntime> result = new ArrayList<>();
        for (JavaRuntime runtime : list) {
            String key = runtime.home.toString().toLowerCase();
            if (seen.add(key)) {
                result.add(runtime);
            }
        }
        return result;
    }

    private void collectLauncherRuntimes(Set<Path> out) {
        if (!Files.isDirectory(runtimeDir)) {
            return;
        }
        for (Path dir : FileUtil.dirs(runtimeDir)) {
            Path exe = javaExe(dir);
            if (exe != null) {
                out.add(exe);
            }
        }
    }

    private void collectMojangRuntimes(Set<Path> out) {
        Path mojangRuntime = mcRoot.resolve("runtime");
        if (!Files.isDirectory(mojangRuntime)) {
            return;
        }
        for (Path component : FileUtil.dirs(mojangRuntime)) {
            for (Path platform : FileUtil.dirs(component)) {
                for (Path inner : FileUtil.dirs(platform)) {
                    Path exe = javaExe(inner);
                    if (exe != null) {
                        out.add(exe);
                    }
                }
            }
        }
    }

    private void collectSystemCandidates(Set<Path> out) {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            Path exe = javaExe(Path.of(javaHome));
            if (exe != null) {
                out.add(exe);
            }
        }
        // PATH entries
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String entry : pathEnv.split(java.io.File.pathSeparator)) {
                if (entry.isBlank()) {
                    continue;
                }
                try {
                    Path exe = javaExe(Path.of(entry));
                    if (exe != null) {
                        out.add(exe);
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
        // Common install roots
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        List<Path> roots = new ArrayList<>();
        for (String base : new String[]{programFiles, programFilesX86}) {
            if (base == null || base.isBlank()) {
                continue;
            }
            Path root = Path.of(base);
            roots.add(root.resolve("Java"));
            roots.add(root.resolve("Eclipse Adoptium"));
            roots.add(root.resolve("Eclipse Foundation"));
            roots.add(root.resolve("Microsoft"));
            roots.add(root.resolve("Zulu"));
            roots.add(root.resolve("BellSoft"));
            roots.add(root.resolve("Amazon Corretto"));
            roots.add(root.resolve("Semeru"));
            roots.add(root.resolve("GraalVM"));
        }
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            for (Path dir : FileUtil.dirs(root)) {
                Path exe = javaExe(dir);
                if (exe != null) {
                    out.add(exe);
                } else {
                    // e.g. Microsoft\jdk-17.0.9.8-hotspot and Zulu\zulu-21
                    for (Path nested : FileUtil.dirs(dir)) {
                        Path inner = javaExe(nested);
                        if (inner != null) {
                            out.add(inner);
                        }
                    }
                }
            }
        }
        // Registry (Oracle / any vendor registering JavaHome)
        for (String line : registryJavaHomes()) {
            Path home = Path.of(line);
            Path exe = javaExe(home);
            if (exe != null) {
                out.add(exe);
            }
        }
    }

    private static List<String> registryJavaHomes() {
        List<String> homes = new ArrayList<>();
        if (!OsInfo.isWindows()) {
            return homes;
        }
        String[][] queries = {
                {"reg", "query", "HKLM\\SOFTWARE\\JavaSoft", "/s", "/v", "JavaHome"},
                {"reg", "query", "HKLM\\SOFTWARE\\WOW6432Node\\JavaSoft", "/s", "/v", "JavaHome"}
        };
        for (String[] query : queries) {
            try {
                Process process = new ProcessBuilder(query).redirectErrorStream(true).start();
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int idx = line.indexOf("REG_SZ");
                        if (idx > 0) {
                            String value = line.substring(idx + "REG_SZ".length()).trim();
                            if (!value.isBlank()) {
                                homes.add(value);
                            }
                        }
                    }
                }
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
        return homes;
    }

    private static Path javaExe(Path home) {
        if (home == null) {
            return null;
        }
        Path exe = home.resolve("bin").resolve(OsInfo.isWindows() ? "java.exe" : "java");
        return Files.isRegularFile(exe) ? exe : null;
    }

    /** Picks the closest runtime for a version's required Java major. */
    public JavaRuntime best(int requiredMajor) {
        List<JavaRuntime> list = installed();
        JavaRuntime exact = null;
        JavaRuntime newer = null;
        for (JavaRuntime runtime : list) {
            if (runtime.major == requiredMajor) {
                exact = runtime;
                break;
            }
            if (runtime.major > requiredMajor && (newer == null || runtime.major < newer.major)) {
                newer = runtime;
            }
        }
        if (exact != null) {
            return exact;
        }
        if (newer != null) {
            return newer;
        }
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    // -------------------------------------------------------------- mojang

    public List<MojangOption> mojangOptions() {
        List<MojangOption> options = new ArrayList<>();
        try {
            Path cache = dataRoot.resolve("cache").resolve("java_runtime_all.json");
            String text;
            if (Files.isRegularFile(cache) && Files.getLastModifiedTime(cache).toMillis()
                    > System.currentTimeMillis() - 6 * 3600_000L) {
                text = FileUtil.readString(cache);
            } else {
                text = Http.text(MOJANG_RUNTIME_INDEX, 4 * 1024 * 1024);
                FileUtil.writeString(cache, text);
            }
            Map<String, Object> root = Json.obj(Json.parse(text));
            String platform = mojangPlatform();
            Map<String, Object> platformMap = Json.objOrEmpty(root.get(platform));
            for (var entry : platformMap.entrySet()) {
                String component = entry.getKey();
                if (!component.startsWith("java-runtime") && !component.equals("jre-legacy")) {
                    continue;
                }
                List<Object> versions = Json.arrOrEmpty(entry.getValue());
                if (versions.isEmpty()) {
                    continue;
                }
                Map<String, Object> first = Json.objOrEmpty(versions.get(0));
                String versionName = Json.getStr("", first, "version", "name");
                String released = Json.getStr("", first, "version", "released");
                String manifestUrl = Json.getStr(null, first, "manifest", "url");
                String sha1 = Json.getStr(null, first, "manifest", "sha1");
                long size = Json.lng(Json.get(first, "manifest", "size"), 0);
                if (manifestUrl != null) {
                    options.add(new MojangOption(component, versionName, released, platform, manifestUrl, sha1, size));
                }
            }
        } catch (Exception e) {
            Log.warn("获取 Mojang Java 清单失败: %s", e.getMessage());
        }
        options.sort(Comparator.comparingInt(o -> JavaRuntime.parseMajor(o.versionName())));
        return options;
    }

    private static String mojangPlatform() {
        if (OsInfo.isWindows()) {
            return switch (OsInfo.arch()) {
                case "arm64" -> "windows-arm64";
                case "x86" -> "windows-x86";
                default -> "windows-x64";
            };
        }
        if (OsInfo.isMac()) {
            return "arm64".equals(OsInfo.arch()) ? "mac-os-arm64" : "mac-os";
        }
        return "x86".equals(OsInfo.arch()) ? "linux-i386" : "linux";
    }

    public JavaRuntime installMojang(MojangOption option, Progress progress) {
        Path dest = runtimeDir.resolve("mojang-" + option.component() + "-" + option.versionName());
        Path existing = javaExe(dest);
        if (existing != null) {
            return JavaRuntime.probe(existing, "启动器下载");
        }
        progress.note("获取运行时清单");
        try {
            Map<String, Object> manifest = Json.obj(Json.parse(Http.text(option.manifestUrl(), 8 * 1024 * 1024)));
            Map<String, Object> files = Json.objOrEmpty(manifest.get("files"));
            List<Downloader.Item> items = new ArrayList<>();
            for (var entry : files.entrySet()) {
                Map<String, Object> file = Json.objOrEmpty(entry.getValue());
                String type = Json.str(file.get("type"), "file");
                Path target = dest.resolve(entry.getKey().replace('/', java.io.File.separatorChar));
                if ("directory".equals(type)) {
                    FileUtil.ensureDir(target);
                    continue;
                }
                if (!"file".equals(type)) {
                    continue;
                }
                String url = Json.getStr(null, file, "downloads", "raw", "url");
                String sha1 = Json.getStr(null, file, "downloads", "raw", "sha1");
                long size = Json.lng(Json.get(file, "downloads", "raw", "size"), 0);
                if (url != null) {
                    items.add(Downloader.Item.of(entry.getKey(), url, target, sha1, size));
                }
            }
            new Downloader(8).downloadAll(items, progress);
            Path exe = javaExe(dest);
            if (exe == null) {
                throw new IOException("下载的运行时缺少 bin/java");
            }
            JavaRuntime runtime = JavaRuntime.probe(exe, "启动器下载");
            if (runtime != null) {
                cache.add(runtime);
            }
            return runtime;
        } catch (Exception e) {
            FileUtil.deleteRecursive(dest);
            throw new RuntimeException("安装 Mojang Java 运行时失败: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------ adoptium

    public List<AdoptiumOption> adoptiumOptions() {
        List<AdoptiumOption> options = new ArrayList<>();
        try {
            String text = Http.text("https://api.adoptium.net/v3/info/available_releases", 512 * 1024);
            Map<String, Object> map = Json.obj(Json.parse(text));
            List<Object> lts = Json.arrOrEmpty(map.get("available_lts_releases"));
            List<Object> all = Json.arrOrEmpty(map.get("available_releases"));
            for (Object o : lts) {
                int major = Json.integer(o, 0);
                if (major >= 8) {
                    options.add(new AdoptiumOption(major, true));
                }
            }
            int recent = Json.integer(map.get("most_recent_feature_release"), 0);
            if (recent >= 8 && options.stream().noneMatch(o -> o.major() == recent)) {
                options.add(new AdoptiumOption(recent, false));
            }
        } catch (Exception e) {
            Log.warn("获取 Adoptium 版本列表失败: %s", e.getMessage());
            for (int major : new int[]{8, 11, 17, 21, 25}) {
                options.add(new AdoptiumOption(major, true));
            }
        }
        options.sort(Comparator.comparingInt(AdoptiumOption::major));
        return options;
    }

    public JavaRuntime installAdoptium(int major, Progress progress) {
        return installAdoptium(major, JavaSource.ADOPTIUM_API, progress);
    }

    public JavaRuntime installAdoptium(int major, JavaSource source, Progress progress) {
        Path dest = runtimeDir.resolve("temurin-" + major + "-" + OsInfo.arch());
        Path existing = javaExe(dest);
        if (existing != null) {
            return JavaRuntime.probe(existing, "启动器下载");
        }
        String url;
        try {
            url = source == JavaSource.TUNA ? tunaJreUrl(major) : adoptiumApiUrl(major);
        } catch (IOException e) {
            throw new RuntimeException("获取 Java 下载地址失败: " + e.getMessage(), e);
        }
        Path tempZip = dataRoot.resolve("cache").resolve("temurin-" + major + ".zip");
        Path tempDir = dataRoot.resolve("cache").resolve("temurin-" + major + "-extract");
        try {
            progress.note("下载 Java " + major + " (Temurin)");
            FileUtil.deleteRecursive(tempDir);
            new Downloader(4).downloadAll(List.of(
                    Downloader.Item.of("Java " + major, url, tempZip, null, 0)), progress);
            progress.note("解压 Java " + major);
            FileUtil.deleteRecursive(tempDir);
            FileUtil.extractZip(tempZip, tempDir, name -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".diz") || lower.contains("src.zip") || lower.contains("/src/");
            });
            Path inner = FileUtil.dirs(tempDir).stream()
                    .filter(p -> Files.isDirectory(p.resolve("bin")))
                    .findFirst().orElse(tempDir);
            FileUtil.deleteRecursive(dest);
            FileUtil.ensureDir(dest);
            try (var stream = Files.list(inner)) {
                for (Path child : (Iterable<Path>) stream::iterator) {
                    FileUtil.move(child, dest.resolve(child.getFileName().toString()));
                }
            }
            Files.deleteIfExists(tempZip);
            FileUtil.deleteRecursive(tempDir);
            Path exe = javaExe(dest);
            if (exe == null) {
                throw new IOException("解压后缺少 bin/java");
            }
            JavaRuntime runtime = JavaRuntime.probe(exe, "启动器下载");
            if (runtime == null) {
                throw new IOException("下载的 Java 无法启动");
            }
            cache.add(runtime);
            return runtime;
        } catch (Exception e) {
            FileUtil.deleteRecursive(dest);
            throw new RuntimeException("安装 Temurin Java " + major + " 失败: " + e.getMessage(), e);
        }
    }

    public void delete(JavaRuntime runtime) {
        Path home = runtime.home.toAbsolutePath().normalize();
        if (!home.startsWith(runtimeDir.toAbsolutePath().normalize())) {
            throw new IllegalStateException("只能删除启动器下载的 Java");
        }
        FileUtil.deleteRecursive(home);
        cache.removeIf(r -> r.home.equals(runtime.home));
    }

    private static String adoptiumApiUrl(int major) {
        String os = OsInfo.isWindows() ? "windows" : OsInfo.isMac() ? "mac" : "linux";
        String arch = switch (OsInfo.arch()) {
            case "arm64" -> "aarch64";
            case "x86" -> "x86";
            default -> "x64";
        };
        return "https://api.adoptium.net/v3/binary/latest/" + major + "/ga/" + os + "/" + arch
                + "/jre/hotspot/normal/eclipse";
    }

    /** Tsinghua mirrors Adoptium releases and exposes an HTML index we can parse. */
    private static String tunaJreUrl(int major) throws IOException {
        if (!OsInfo.isWindows() || !"x64".equals(OsInfo.arch())) {
            return adoptiumApiUrl(major);
        }
        String directory = Mirror.TUNA + "/Adoptium/" + major + "/jre/x64/windows/";
        String html = Http.text(directory, 4 * 1024 * 1024);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("href=\"([^\"]+\\.zip)\"").matcher(html);
        String best = null;
        while (matcher.find()) {
            String name = matcher.group(1);
            String lower = name.toLowerCase();
            if (!lower.contains("jre") || !lower.contains("windows")) {
                continue;
            }
            if (best == null || compareVersionNames(name, best) > 0) {
                best = name;
            }
        }
        if (best == null) {
            throw new IOException("清华镜像中未找到 Java " + major + " 的 Windows 版本");
        }
        return directory + best;
    }

    private static int compareVersionNames(String a, String b) {
        java.util.List<Integer> na = numbers(a);
        java.util.List<Integer> nb = numbers(b);
        for (int i = 0; i < Math.max(na.size(), nb.size()); i++) {
            int va = i < na.size() ? na.get(i) : 0;
            int vb = i < nb.size() ? nb.get(i) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return a.compareTo(b);
    }

    private static java.util.List<Integer> numbers(String text) {
        java.util.List<Integer> values = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(text);
        while (matcher.find()) {
            try {
                values.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
            }
        }
        return values;
    }
}
