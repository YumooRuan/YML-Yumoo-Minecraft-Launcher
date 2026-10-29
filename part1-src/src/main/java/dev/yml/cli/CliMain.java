package dev.yml.cli;

import dev.yml.core.AppPaths;
import dev.yml.core.auth.Account;
import dev.yml.core.game.GameLauncher;
import dev.yml.core.game.LaunchOptions;
import dev.yml.core.game.LocalVersions;
import dev.yml.core.game.VersionInfo;
import dev.yml.core.java.JavaManager;
import dev.yml.core.java.JavaRuntime;
import dev.yml.core.net.Mirror;
import dev.yml.core.util.Log;
import dev.yml.core.util.Progress;
import dev.yml.core.util.FileUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Headless entry point used for development and diagnostics. */
public final class CliMain {

    private static Path dataRoot = AppPaths.defaultDataRoot();
    private static Path mcRoot = AppPaths.defaultMcRoot();
    private static Mirror.Source source = Mirror.Source.AUTO;

    public static void main(String[] args) throws Exception {
        List<String> rest = new ArrayList<>();
        String command = null;
        String javaPath = null;
        String name = "Steve";
        int memory = 4096;
        int seconds = 0;
        boolean isolated = true;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--root" -> mcRoot = Path.of(args[++i]);
                case "--data" -> dataRoot = Path.of(args[++i]);
                case "--java" -> javaPath = args[++i];
                case "--name" -> name = args[++i];
                case "--mem" -> memory = Integer.parseInt(args[++i]);
                case "--seconds" -> seconds = Integer.parseInt(args[++i]);
                case "--source" -> source = Mirror.Source.valueOf(args[++i].toUpperCase());
                case "--no-isolate" -> isolated = false;
                default -> {
                    if (command == null) {
                        command = arg;
                    } else {
                        rest.add(arg);
                    }
                }
            }
        }
        FileUtil.ensureDir(dataRoot);
        Log.init(dataRoot.resolve("logs").resolve("cli.log"), true);

        switch (command == null ? "help" : command) {
            case "list" -> list();
            case "java" -> javaList();
            case "install" -> install(rest);
            case "launch" -> launch(rest, javaPath, name, memory, isolated, seconds);
            case "install-java" -> installJava(rest);
            case "fabric" -> installFabric(rest);
            case "forge" -> installForge(rest);
            case "help" -> help();
            default -> {
                System.err.println("未知命令: " + command);
                help();
            }
        }
    }

    private static void help() {
        System.out.println("""
                Yumoo Minecraft Launcher CLI
                  list                         列出已安装版本
                  java                         列出检测到的 Java
                  install <版本ID> [版本清单URL]  下载并安装原版版本
                  install-java <主版本|mojang:组件> 下载 Java 运行时
                  launch <版本ID> [选项]        启动游戏
                选项: --root <游戏目录> --data <数据目录> --java <java.exe> --name <玩家名>
                      --mem <MB> --seconds <N> --source OFFICIAL|MIRROR|AUTO --no-isolate
                """);
    }

    private static void list() {
        System.out.println("游戏目录: " + mcRoot);
        List<LocalVersions.Installed> versions = LocalVersions.scan(mcRoot);
        if (versions.isEmpty()) {
            System.out.println("(没有已安装版本)");
            return;
        }
        for (LocalVersions.Installed installed : versions) {
            String java = installed.info() == null ? "?" : "Java " + installed.info().javaMajor;
            System.out.printf("%-42s %-9s %-9s %s%s%n", installed.id(), installed.loader().label(),
                    java, installed.baseVersion(), installed.broken() ? "  [损坏: " + installed.error() + "]" : "");
        }
    }

    private static void javaList() {
        JavaManager manager = new JavaManager(dataRoot, mcRoot);
        List<JavaRuntime> runtimes = manager.refresh();
        System.out.println("启动器运行时目录: " + manager.runtimeDir());
        for (JavaRuntime runtime : runtimes) {
            System.out.printf("Java %-3d %-14s %-22s %s%s%n", runtime.major, runtime.arch, runtime.vendor,
                    runtime.executable, runtime.javac ? "  [JDK]" : "");
        }
        if (runtimes.isEmpty()) {
            System.out.println("(未检测到 Java)");
        }
    }

    private static void install(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("用法: install <版本ID> [版本清单URL]");
            return;
        }
        String id = args.get(0);
        String url = args.size() > 1 ? args.get(1) : null;
        if (url == null) {
            var manifest = dev.yml.core.game.VersionManifest.load(source,
                    dataRoot.resolve("cache").resolve("version_manifest_v2.json"), true);
            for (var remote : manifest.versions) {
                if (remote.id().equals(id)) {
                    url = remote.url();
                    break;
                }
            }
        }
        if (url == null) {
            System.out.println("版本清单中找不到 " + id);
            return;
        }
        long start = System.currentTimeMillis();
        GameLauncher.installVanilla(id, url, mcRoot, source, 16, consoleProgress());
        System.out.printf("安装完成，用时 %.1f 秒%n", (System.currentTimeMillis() - start) / 1000.0);
    }

    private static void installFabric(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("用法: fabric <Minecraft版本> [Fabric Loader 版本]");
            return;
        }
        String mc = args.get(0);
        String loader = args.size() > 1 ? args.get(1) : null;
        if (loader == null) {
            var versions = dev.yml.core.loader.FabricInstaller.loaders(mc, source);
            if (versions.isEmpty()) {
                System.out.println("没有找到 " + mc + " 的 Fabric 版本");
                return;
            }
            loader = versions.get(0).version();
        }
        System.out.println("安装 Fabric " + loader + " for " + mc);
        String id = dev.yml.core.loader.FabricInstaller.install(mc, loader, mcRoot, source, 16,
                consoleProgress());
        System.out.println("完成: " + id);
    }

    private static void installForge(List<String> args) throws Exception {
        if (args.isEmpty()) {
            System.out.println("用法: forge <Minecraft版本>");
            return;
        }
        String mc = args.get(0);
        var versions = dev.yml.core.loader.ForgeInstaller.versions(mc, source);
        if (versions.isEmpty()) {
            System.out.println("没有找到 " + mc + " 的 Forge 版本");
            return;
        }
        var version = versions.get(versions.size() - 1);
        JavaManager manager = new JavaManager(dataRoot, mcRoot);
        JavaRuntime runtime = manager.best(17);
        if (runtime == null) {
            System.out.println("缺少 Java，先运行 install-java 17");
            return;
        }
        System.out.println("安装 Forge " + version.forgeVersion() + " for " + mc);
        String id = dev.yml.core.loader.ForgeInstaller.install(version, mcRoot, runtime.executable,
                dataRoot.resolve("cache"), source, consoleProgress(), line -> System.out.println("  | " + line));
        System.out.println("完成: " + id);
    }

    private static void installJava(List<String> args) {        if (args.isEmpty()) {
            System.out.println("用法: install-java <主版本> 或 install-java mojang:<组件>");
            return;
        }
        JavaManager manager = new JavaManager(dataRoot, mcRoot);
        String target = args.get(0);
        JavaRuntime runtime;
        if (target.startsWith("mojang:")) {
            String component = target.substring("mojang:".length());
            var option = manager.mojangOptions().stream()
                    .filter(o -> o.component().equals(component))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("找不到组件 " + component));
            System.out.println("安装 " + option.label() + " (" + option.versionName() + ")");
            runtime = manager.installMojang(option, consoleProgress());
        } else {
            int major = Integer.parseInt(target);
            System.out.println("安装 Temurin Java " + major);
            runtime = manager.installAdoptium(major, consoleProgress());
        }
        System.out.println("完成: " + runtime);
    }

    private static void launch(List<String> args, String javaPath, String name, int memory, boolean isolated,
                               int seconds) throws Exception {
        if (args.isEmpty()) {
            System.out.println("用法: launch <版本ID>");
            return;
        }
        String id = args.get(0);
        LaunchOptions options = LaunchOptions.of(mcRoot, id);
        options.memoryMb = memory;
        options.minMemoryMb = Math.min(512, memory / 2);
        options.username = name;
        options.uuid = Account.offlineUuid(name);
        options.isolated = isolated;
        options.source = source;
        options.threads = 16;

        VersionInfo info = VersionInfo.load(options.versionDir().resolve(id + ".json"),
                mcRoot.resolve("versions"));
        System.out.println("版本: " + id + " | 加载器: " + dev.yml.core.game.LoaderType.detect(info).label()
                + " | 需要 Java " + info.javaMajor);

        if (javaPath != null) {
            options.javaExecutable = Path.of(javaPath);
        } else {
            JavaManager manager = new JavaManager(dataRoot, mcRoot);
            JavaRuntime runtime = manager.best(Math.max(8, info.javaMajor));
            if (runtime == null) {
                System.out.println("没有可用的 Java，先运行 install-java");
                return;
            }
            System.out.println("使用 Java: " + runtime.executable + " (" + runtime.version + ")");
            options.javaExecutable = runtime.executable;
        }

        long start = System.currentTimeMillis();
        GameLauncher.Prepared prepared = GameLauncher.prepare(options, consoleProgress());
        System.out.printf("准备完成，用时 %.1f 秒%n", (System.currentTimeMillis() - start) / 1000.0);
        System.out.println("日志文件: " + prepared.logFile());
        Process process = GameLauncher.launch(prepared, line -> System.out.println("  | " + line));
        System.out.println("游戏进程已启动, PID=" + process.pid());
        if (seconds > 0) {
            boolean exited = process.waitFor(seconds, TimeUnit.SECONDS);
            if (!exited) {
                System.out.println("达到 " + seconds + " 秒，结束进程");
                process.destroy();
                process.waitFor(10, TimeUnit.SECONDS);
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            } else {
                System.out.println("游戏已退出, 退出码 " + process.exitValue());
            }
        } else {
            int exit = process.waitFor();
            System.out.println("游戏已退出, 退出码 " + exit);
        }
    }

    private static Progress consoleProgress() {
        long[] last = {0, System.currentTimeMillis()};
        String[] lastNote = {""};
        return (done, total, note) -> {
            long now = System.currentTimeMillis();
            String percent = total > 0 ? String.format(" %5.1f%%", done * 100.0 / total)
                    : (done > 0 ? " " + FileUtil.humanSize(done) : "");
            if (note != null && !note.isBlank() && now - last[0] > 1000) {
                last[0] = now;
                if (!note.equals(lastNote[0]) || percent.length() > 0) {
                    lastNote[0] = note;
                }
                System.out.println("  * " + note + percent);
            }
        };
    }
}
