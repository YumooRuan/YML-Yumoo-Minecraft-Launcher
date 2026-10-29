package dev.yml.core.game;

import dev.yml.core.json.Json;
import dev.yml.core.net.Downloader;
import dev.yml.core.net.Mirror;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;
import dev.yml.core.util.OsInfo;
import dev.yml.core.util.Progress;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Builds the java command line, ensures game files, and runs the game. */
public final class GameLauncher {

    public record Prepared(List<String> command, Path gameDir, Path javaExe, VersionInfo info, Path logFile) {
        public String commandLine() {
            StringBuilder sb = new StringBuilder();
            for (String part : command) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(part.contains(" ") ? '"' + part + '"' : part);
            }
            return sb.toString();
        }
    }

    private GameLauncher() {
    }

    // ------------------------------------------------------------- prepare

    public static Prepared prepare(LaunchOptions options, Progress progress) {
        if (options.mcRoot == null || options.versionId == null) {
            throw new IllegalStateException("未指定游戏目录或版本");
        }
        Path versionsDir = options.mcRoot.resolve("versions");
        Path versionDir = options.versionDir();
        Path versionJson = versionDir.resolve(options.versionId + ".json");
        if (!Files.isRegularFile(versionJson)) {
            throw new IllegalStateException("版本不存在: " + options.versionId);
        }
        VersionInfo info = VersionInfo.load(versionJson, versionsDir);

        Path javaExe = options.javaExecutable;
        if (javaExe == null) {
            throw new IllegalStateException("未选择 Java 运行时");
        }

        ensureFiles(info, options.mcRoot, versionDir, options.source, options.threads, progress);

        Map<String, Boolean> features = Rule.defaultFeatures(options.width > 0 && options.height > 0);
        features.put("is_demo_user", options.demo);

        Path librariesDir = options.mcRoot.resolve("libraries");
        Path assetsDir = options.mcRoot.resolve("assets");
        Path nativesDir = versionDir.resolve("natives");

        List<String> classpath = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<Library> extraction = new ArrayList<>();
        for (Library library : info.libraries) {
            if (!library.allowed(features)) {
                continue;
            }
            String path = library.path();
            if (path == null || path.isBlank()) {
                continue;
            }
            Path jar = librariesDir.resolve(path);
            if (seen.add(jar.toAbsolutePath().normalize().toString())) {
                classpath.add(jar.toString());
            }
            if (library.needsExtraction()) {
                extraction.add(library);
            }
        }

        Path clientJar = clientJar(options, versionDir, info);
        if (clientJar != null && seen.add(clientJar.toAbsolutePath().normalize().toString())) {
            classpath.add(clientJar.toString());
        }

        extractNatives(info, librariesDir, nativesDir, extraction, features);

        Path assetIndexFile = assetsDir.resolve("indexes").resolve(info.assets + ".json");
        if (!Files.isRegularFile(assetIndexFile)) {
            Log.warn("资源索引缺失: %s", assetIndexFile);
        }

        Map<String, String> vars = variables(options, info, versionDir, librariesDir, assetsDir, nativesDir,
                clientJar, classpath);

        List<String> command = new ArrayList<>();
        command.add(javaExe.toString());

        boolean versionProvidesJvm = info.jvmArgs.stream()
                .anyMatch(a -> String.valueOf(argValue(a)).contains("${classpath}"));
        if (options.addDefaultJvmArgs) {
            command.add("-Xms" + Math.max(128, options.minMemoryMb) + "M");
            command.add("-Xmx" + Math.max(256, options.memoryMb) + "M");
            command.add("-Dfile.encoding=UTF-8");
            command.add("-Dstdout.encoding=UTF-8");
            command.add("-Dstderr.encoding=UTF-8");
        }
        if (options.addOptimizedJvmArgs) {
            command.add("-XX:+UseG1GC");
            command.add("-XX:+ParallelRefProcEnabled");
            command.add("-XX:MaxGCPauseMillis=200");
        }
        command.addAll(options.globalJvmArgs);
        if (OsInfo.isMac()) {
            command.add("-XstartOnFirstThread");
        }
        if (!versionProvidesJvm) {
            String nativePath = options.nativeLibraryPath != null && !options.nativeLibraryPath.isBlank()
                    ? pathForArg(Path.of(options.nativeLibraryPath)) : pathForArg(nativesDir);
            command.add("-Djava.library.path=" + nativePath);
            command.add("-Dminecraft.launcher.brand=" + options.launcherName);
            command.add("-Dminecraft.launcher.version=" + options.launcherVersion);
        }
        if (info.logArgument != null && info.logConfigId != null) {
            Path logConfig = assetsDir.resolve("log_configs").resolve(info.logConfigId);
            if (Files.isRegularFile(logConfig)) {
                // log4j parses this as a URL, so a bare Windows path fails with "unknown protocol: c".
                command.add(info.logArgument.replace("${path}", logConfig.toUri().toString()));
            }
        }
        if (!versionProvidesJvm) {
            command.add("-cp");
            command.add(String.join(java.io.File.pathSeparator, classpath));
        }
        command.addAll(expandArgs(info.jvmArgs, features, vars));
        command.addAll(options.extraJvmArgs);
        command.add(info.mainClass);

        if (info.hasLegacyArguments()) {
            for (String token : info.minecraftArguments.trim().split("\\s+")) {
                if (!token.isEmpty()) {
                    command.add(expand(token, vars));
                }
            }
        } else {
            command.addAll(expandArgs(info.gameArgs, features, vars));
        }
        command.addAll(options.extraGameArgs);

        Path logDir = options.gameDir().resolve("logs");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path logFile = logDir.resolve("yml-" + stamp + ".log");
        return new Prepared(command, options.gameDir(), javaExe, info, logFile);
    }

    private static String argValue(Object arg) {
        if (arg instanceof String s) {
            return s;
        }
        if (arg instanceof Map<?, ?> map) {
            Object value = map.get("value");
            return value instanceof String s ? s : String.valueOf(value);
        }
        return "";
    }

    private static Map<String, String> variables(LaunchOptions options, VersionInfo info, Path versionDir,
                                                 Path librariesDir, Path assetsDir, Path nativesDir,
                                                 Path clientJar, List<String> classpath) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("auth_player_name", options.username);
        vars.put("auth_uuid", options.uuid);
        vars.put("auth_access_token", options.accessToken);
        vars.put("auth_session", "token:" + options.accessToken + ":" + options.uuid);
        vars.put("auth_xuid", "0");
        vars.put("clientid", "0");
        vars.put("user_type", options.userType);
        vars.put("user_properties", "{}");
        vars.put("version_name", options.versionId);
        vars.put("version_type", info.type == null ? "release" : info.type);
        vars.put("game_directory", pathForArg(options.gameDir()));
        vars.put("game_assets", pathForArg(assetsDir.resolve("virtual").resolve("legacy")));
        vars.put("assets_root", pathForArg(assetsDir));
        vars.put("assets_index_name", info.assets == null ? "legacy" : info.assets);
        vars.put("natives_directory", pathForArg(nativesDir));
        vars.put("launcher_name", options.launcherName);
        vars.put("launcher_version", options.launcherVersion);
        vars.put("classpath", String.join(java.io.File.pathSeparator, classpath));
        vars.put("classpath_separator", java.io.File.pathSeparator);
        vars.put("library_directory", pathForArg(librariesDir));
        vars.put("resolution_width", String.valueOf(options.width));
        vars.put("resolution_height", String.valueOf(options.height));
        vars.put("quickPlayPath", "");
        vars.put("quickPlaySingleplayer", "");
        vars.put("quickPlayMultiplayer", "");
        vars.put("quickPlayRealms", "");
        if (clientJar != null) {
            vars.put("primary_jar", pathForArg(clientJar));
        }
        return vars;
    }

    private static String pathForArg(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private static List<String> expandArgs(List<Object> rawArgs, Map<String, Boolean> features,
                                           Map<String, String> vars) {
        List<String> out = new ArrayList<>();
        for (Object arg : rawArgs) {
            if (arg instanceof String s) {
                out.add(expand(s, vars));
            } else if (arg instanceof Map<?, ?> map) {
                Map<String, Object> entry = Json.obj(map);
                List<Rule> rules = Rule.parseList(entry.get("rules"));
                if (!Rule.allows(rules, features)) {
                    continue;
                }
                Object value = entry.get("value");
                if (value instanceof String s) {
                    out.add(expand(s, vars));
                } else if (value instanceof List<?> list) {
                    for (Object element : list) {
                        if (element instanceof String s) {
                            out.add(expand(s, vars));
                        }
                    }
                }
            }
        }
        return out;
    }

    private static String expand(String template, Map<String, String> vars) {
        String result = template;
        for (var entry : vars.entrySet()) {
            if (result.contains("${" + entry.getKey() + "}")) {
                result = result.replace("${" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }

    // ------------------------------------------------------------ file sync

    public static void ensureFiles(VersionInfo info, Path mcRoot, Path versionDir, Mirror.Source source,
                                   int threads, Progress progress) {
        Path librariesDir = mcRoot.resolve("libraries");
        Path assetsDir = mcRoot.resolve("assets");
        List<Downloader.Item> items = new ArrayList<>();

        if (info.clientUrl != null && !info.clientUrl.isBlank() && findClientJar(info, versionDir, mcRoot) == null) {
            String jarName = info.jar != null && !info.jar.isBlank() ? info.jar : info.id;
            Path jar = versionDir.resolve(jarName + ".jar");
            List<String> urls = Mirror.candidates(info.clientUrl, source);
            if (source != Mirror.Source.OFFICIAL) {
                urls.add(Mirror.BMCLAPI + "/version/" + info.id + "/client");
            }
            items.add(Downloader.Item.of("客户端 jar", urls, jar, info.clientSha1, info.clientSize));
        }

        Map<String, Boolean> features = Rule.defaultFeatures(true);
        for (Library library : info.libraries) {
            if (!library.allowed(features)) {
                continue;
            }
            String path = library.path();
            if (path != null && !path.isBlank()) {
                // Maven entries such as jinput-platform/twitch-platform only ship natives.
                boolean nativeOnly = library.isNativesOnly()
                        || ("net.java.jinput".equals(library.groupId)
                        && "jinput-platform".equals(library.artifactId));
                if (!nativeOnly) {
                    List<String> urls = new ArrayList<>(Mirror.candidates(library.url(), source));
                    if ("net.java.jinput".equals(library.groupId)) {
                        urls.add("https://repo1.maven.org/maven2/" + path);
                    }
                    items.add(Downloader.Item.of(library.name, urls,
                            librariesDir.resolve(path), library.sha1(), library.size()));
                }
            }
            String nativePath = library.nativePath();
            if (nativePath != null && !nativePath.isBlank()) {
                List<String> nativeUrls = new ArrayList<>(
                        Mirror.candidates(library.nativeUrl(), source));
                if ("net.java.jinput".equals(library.groupId)) {
                    nativeUrls.add("https://repo1.maven.org/maven2/" + nativePath);
                }
                items.add(Downloader.Item.of(library.name + " (natives)",
                        nativeUrls, librariesDir.resolve(nativePath),
                        library.nativeSha1(), library.nativeSize()));
            }
        }

        // Asset index + objects
        if (info.assetIndexId != null && info.assetIndexUrl != null) {
            Path indexFile = assetsDir.resolve("indexes").resolve(info.assetIndexId + ".json");
            items.add(Downloader.Item.of("资源索引", Mirror.candidates(info.assetIndexUrl, source), indexFile,
                    info.assetIndexSha1, info.assetIndexSize));
        }

        // Logging config
        if (info.logConfigUrl != null && info.logConfigId != null) {
            Path logFile = assetsDir.resolve("log_configs").resolve(info.logConfigId);
            items.add(Downloader.Item.of("日志配置", Mirror.candidates(info.logConfigUrl, source), logFile,
                    info.logConfigSha1, info.logConfigSize));
        }

        if (progress != null) {
            progress.note("检查游戏文件");
        }
        new Downloader(Math.max(1, threads)).downloadAll(items, progress);

        if (info.assetIndexId != null) {
            Path indexFile = assetsDir.resolve("indexes").resolve(info.assetIndexId + ".json");
            if (Files.isRegularFile(indexFile)) {
                AssetIndex index = AssetIndex.load(indexFile);
                List<Downloader.Item> assets = AssetIndex.downloads(index, assetsDir, source);
                if (progress != null) {
                    progress.note("下载资源文件 (" + assets.size() + ")");
                }
                new Downloader(Math.max(1, threads)).downloadAll(assets, progress);
                index.materialize(assetsDir);
            }
        }
        if (progress != null) {
            progress.update(1, 1, "游戏文件已就绪");
        }
    }

    private static Path findClientJar(VersionInfo info, Path versionDir, Path mcRoot) {
        String jarName = info.jar != null && !info.jar.isBlank() ? info.jar : info.id;
        Path direct = versionDir.resolve(jarName + ".jar");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        if (info.inheritsFrom != null) {
            Path parent = mcRoot.resolve("versions").resolve(info.inheritsFrom)
                    .resolve(info.inheritsFrom + ".jar");
            if (Files.isRegularFile(parent)) {
                return parent;
            }
        }
        return null;
    }

    private static Path clientJar(LaunchOptions options, Path versionDir, VersionInfo info) {
        Path found = findClientJar(info, versionDir, options.mcRoot);
        if (found != null) {
            return found;
        }
        Path fallback = versionDir.resolve(options.versionId + ".jar");
        return Files.isRegularFile(fallback) ? fallback : null;
    }

    private static void extractNatives(VersionInfo info, Path librariesDir, Path nativesDir,
                                       List<Library> extraction, Map<String, Boolean> features) {
        if (extraction.isEmpty()) {
            return;
        }
        FileUtil.deleteRecursive(nativesDir);
        FileUtil.ensureDir(nativesDir);
        for (Library library : extraction) {
            if (!library.allowed(features)) {
                continue;
            }
            String nativePath = library.nativePath();
            if (nativePath == null) {
                continue;
            }
            Path jar = librariesDir.resolve(nativePath);
            if (!Files.isRegularFile(jar)) {
                Log.warn("缺少 natives: %s", jar);
                continue;
            }
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String name = entry.getName().replace('\\', '/');
                    boolean excluded = false;
                    for (String prefix : library.extractExclude) {
                        if (name.startsWith(prefix)) {
                            excluded = true;
                            break;
                        }
                    }
                    if (excluded || name.contains("META-INF")) {
                        continue;
                    }
                    Path out = nativesDir.resolve(name).normalize();
                    if (!out.startsWith(nativesDir.normalize())) {
                        continue;
                    }
                    FileUtil.ensureDir(out.getParent());
                    try (var in = zip.getInputStream(entry)) {
                        Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (IOException e) {
                Log.warn("解压 natives 失败 %s: %s", jar, e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------- launch

    public static Process launch(Prepared prepared, Consumer<String> onLine) throws IOException {
        Log.info("启动游戏: %s", prepared.info().id);
        Log.debug("命令行: %s", prepared.commandLine());
        FileUtil.ensureDir(prepared.logFile().getParent());
        ProcessBuilder builder = new ProcessBuilder(prepared.command());
        builder.directory(prepared.gameDir().toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        Thread pump = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 var writer = Files.newBufferedWriter(prepared.logFile(), StandardCharsets.UTF_8,
                         java.nio.file.StandardOpenOption.CREATE,
                         java.nio.file.StandardOpenOption.APPEND)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                    if (onLine != null) {
                        onLine.accept(line);
                    }
                }
            } catch (IOException e) {
                Log.debug("游戏日志读取结束: %s", e.getMessage());
            }
        }, "yml-game-log");
        pump.setDaemon(true);
        pump.start();
        return process;
    }

    /** Installs a vanilla version from its remote JSON (loader installers reuse this). */
    public static void installVanilla(String id, String metadataUrl, Path mcRoot, Mirror.Source source,
                                      int threads, Progress progress) {
        Path dir = mcRoot.resolve("versions").resolve(id);
        Path json = dir.resolve(id + ".json");
        if (!Files.isRegularFile(json)) {
            List<String> urls = new ArrayList<>(Mirror.candidates(metadataUrl, source));
            if (source != Mirror.Source.OFFICIAL) {
                urls.add(Mirror.BMCLAPI + "/version/" + id + "/json");
            }
            if (progress != null) {
                progress.note("获取版本信息 " + id);
            }
            new Downloader(1).downloadOne(Downloader.Item.of("版本 " + id, urls, json, null, 0), progress);
        }
        VersionInfo info = VersionInfo.load(json, mcRoot.resolve("versions"));
        ensureFiles(info, mcRoot, dir, source, threads, progress);
    }
}
