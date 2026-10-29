package dev.yml.core.diagnose;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule based analysis of Minecraft logs and crash reports.
 *
 * <p>The goal is to turn the wall of stack traces into one or two actionable findings.
 * When nothing matches, callers should fall back to exporting a shareable .txt log.</p>
 */
public final class LogAnalyzer {

    public record Finding(String severity, String title, String detail, List<String> suggestions,
                          List<String> evidence) {
    }

    public record Report(List<Finding> findings, String crashDescription, String crashReportName,
                         List<String> systemDetails, String analyzedFile, String errorSummary) {

        public boolean confident() {
            return !findings.isEmpty() || (crashDescription != null && !crashDescription.isBlank());
        }

        public String headline() {
            if (!findings.isEmpty()) {
                return findings.get(0).title();
            }
            if (crashDescription != null && !crashDescription.isBlank()) {
                return "游戏崩溃：" + crashDescription;
            }
            return "未能自动定位问题";
        }
    }

    private record Rule(Pattern pattern, String severity, String title, String detail, List<String> suggestions) {
    }

    private static final List<Rule> RULES = new ArrayList<>();

    static {
        add("UnsupportedClassVersionError|has been compiled by a more recent version|class file version",
                "严重", "Java 版本过低",
                "游戏或模组是用更高版本的 Java 编译的，当前运行时无法加载。",
                "在该版本的“游戏设置”里切换到需要的主版本 Java",
                "首页 → 下载 → Java 运行时 安装对应版本（如 Java 21）");
        add("OutOfMemoryError: Java heap space|java\\.lang\\.OutOfMemoryError",
                "严重", "内存不足",
                "游戏堆内存耗尽，常见于大型整合包或分配内存过少。",
                "在“游戏设置”里把最大内存调高（如 6144 MB 或 8192 MB）",
                "关闭占用内存的程序，或减少同时加载的模组");
        add("OutOfMemoryError: Metaspace|Metaspace",
                "严重", "元空间不足",
                "类元数据占满 Metaspace，通常是模组过多。",
                "在 JVM 参数里添加 -XX:MaxMetaspaceSize=512M",
                "减少模组数量");
        add("GLFW error|Failed to create window|Pixel format not accelerated|Failed to create display|OpenGL",
                "严重", "图形环境初始化失败",
                "游戏无法创建 OpenGL 窗口，通常是显卡驱动、远程桌面或虚拟机所致。",
                "更新显卡驱动，并确认没有在远程桌面/虚拟机上运行",
                "检查是否误开了“全屏”参数，尝试窗口模式启动",
                "NVIDIA 用户可在显卡控制面板中把 java.exe 指定为高性能处理器");
        add("Missing or unsupported mandatory dependencies|Missing dependency|requires .* which is missing|is missing",
                "严重", "缺少前置模组",
                "某个模组依赖的其他模组没有安装或版本不符。",
                "按报错里提示的模组名补装前置（如 Fabric API、Cloth Config 等）",
                "检查模组是否全部适用于当前游戏版本");
        add("Duplicate mods|duplicate mod|DuplicateMods",
                "严重", "存在重复模组",
                "同一个模组被加载了两次（常见于 mods 文件夹里有两个版本）。",
                "打开模组文件夹，删除旧版本或重复的 jar");
        add("Mixin apply failed|MixinTransformerError|Mixin.*failed|mixin.*conflict",
                "严重", "Mixin 注入冲突",
                "多个模组同时修改了同一段游戏代码，常见于功能重叠的模组。",
                "逐个禁用最近添加的模组定位冲突源",
                "确认所有模组都支持当前的 Forge / NeoForge / Fabric 版本");
        add("NoSuchMethodError|NoClassDefFoundError|AbstractMethodError|NoSuchFieldError",
                "严重", "模组与游戏版本不匹配",
                "某个类/方法在运行时不存在，通常是模组版本与游戏或加载器版本不匹配。",
                "核对报错中的模组名，换成适配当前 Minecraft 版本的版本",
                "如果是加载器本身的类，重新安装一次加载器");
        add("Expected BEGIN_OBJECT but was STRING|JsonSyntaxException",
                "严重", "启动参数格式错误",
                "游戏把启动参数当作 JSON 解析时失败，常见于 user_properties / 账户属性不正确。",
                "更新到最新启动器后重试",
                "检查实例的额外游戏参数里是否覆盖了 --userProperties");
        add("ClassNotFoundException: net\\.minecraft|Could not find or load main class net\\.minecraft",
                "严重", "游戏核心文件缺失",
                "客户端 jar 或版本 JSON 不完整，可能是下载中断导致。",
                "在下载页对该版本点击“重新安装”，让启动器补齐文件");
        add("Invalid maximum heap size|Unrecognized option|Could not create the Java Virtual Machine|Unrecognized VM option",
                "严重", "JVM 参数错误",
                "启动参数不被当前 Java 支持。",
                "检查“游戏设置 → JVM 参数”里是否有拼写错误",
                "确认使用的 Java 版本支持这些参数（如 G1GC 相关参数）");
        add("Could not download|Failed to download|DownloadException|Connection reset|SSLHandshakeException|UnknownHostException",
                "警告", "网络下载失败",
                "启动器或游戏在下载依赖时连接失败。",
                "在设置里切换下载源（官方 / BMCLAPI / 阿里云）后重试",
                "检查网络或代理设置");
        add("AccessDeniedException|being used by another process|另一个程序正在使用此文件",
                "警告", "文件被占用或无权限",
                "日志/存档文件被其他程序占用，或游戏目录没有写入权限。",
                "关闭其他启动器（HMCL / PCL）后重试",
                "把游戏目录放到非系统盘或授予写入权限");
        add("Incompatible mod set|Incompatible mods found|incompatible with",
                "严重", "模组互相不兼容",
                "Fabric/Forge 检测到模组依赖冲突。",
                "根据报错里列出的模组调整版本");
        add("java\\.lang\\.IllegalStateException: GLFW|Failed to initialize GLFW|GLFW not initialized",
                "严重", "GLFW 初始化失败",
                "窗口系统初始化失败，常见于缺少运行库或驱动异常。",
                "安装/修复 Visual C++ 运行库，更新显卡驱动");
        add("java\\.lang\\.ExceptionInInitializerError",
                "警告", "模组静态初始化失败",
                "某个模组在加载阶段抛出了异常。",
                "查看异常堆栈里的模组包名，更新或移除该模组");
        add("The game crashed whilst|Minecraft Crash Report",
                "严重", "游戏崩溃",
                "游戏生成了崩溃报告，详情见下方。",
                "查看崩溃报告中的 Description 与 Caused by 行");
        add("Unable to access|failed to open file|FileNotFoundException",
                "警告", "文件访问失败",
                "游戏无法读取某个文件。",
                "检查游戏目录是否完整，防病毒软件是否拦截");
    }

    private static void add(String regex, String severity, String title, String detail, String... suggestions) {
        RULES.add(new Rule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), severity, title, detail,
                List.of(suggestions)));
    }

    private LogAnalyzer() {
    }

    public static Report analyzeFiles(Path logFile, Path crashReport) {
        StringBuilder builder = new StringBuilder();
        if (logFile != null && Files.isRegularFile(logFile)) {
            try {
                builder.append(Files.readString(logFile, StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }
        String crashText = null;
        if (crashReport != null && Files.isRegularFile(crashReport)) {
            try {
                crashText = Files.readString(crashReport, StandardCharsets.UTF_8);
                builder.append("\n\n").append(crashText);
            } catch (IOException ignored) {
            }
        }
        return analyze(builder.toString(), crashText,
                crashReport == null ? null : crashReport.getFileName().toString(),
                logFile == null ? "" : logFile.toString());
    }

    public static Report analyze(String logText, String crashText, String crashName, String analyzedFile) {
        String text = logText == null ? "" : logText;
        List<Finding> findings = new ArrayList<>();
        for (Rule rule : RULES) {
            List<String> evidence = new ArrayList<>();
            Matcher matcher = rule.pattern.matcher(text);
            while (matcher.find() && evidence.size() < 4) {
                String line = lineAt(text, matcher.start());
                if (line.length() > 220) {
                    line = line.substring(0, 220) + "...";
                }
                if (!evidence.contains(line)) {
                    evidence.add(line);
                }
            }
            if (!evidence.isEmpty()) {
                findings.add(new Finding(rule.severity, rule.title, rule.detail, rule.suggestions, evidence));
            }
        }

        String description = null;
        List<String> systemDetails = new ArrayList<>();
        String effectiveCrash = crashText;
        if (effectiveCrash == null && text.contains("---- Minecraft Crash Report ----")) {
            effectiveCrash = text.substring(text.indexOf("---- Minecraft Crash Report ----"));
        }
        if (effectiveCrash != null) {
            Matcher desc = Pattern.compile("^\\s*Description:\\s*(.+)$", Pattern.MULTILINE).matcher(effectiveCrash);
            if (desc.find()) {
                description = desc.group(1).trim();
            }
            int details = effectiveCrash.indexOf("-- System Details --");
            if (details >= 0) {
                String[] lines = effectiveCrash.substring(details).split("\\R");
                for (int i = 0; i < lines.length && systemDetails.size() < 14; i++) {
                    String line = lines[i].trim();
                    if (line.isEmpty() || line.startsWith("--") || line.startsWith("Stacktrace")) {
                        continue;
                    }
                    if (line.matches("(Minecraft Version|Java Version|Memory|CPU|Fabric|Forge|NeoForge|Launcher|Operating System|GL info|Graphics).*")) {
                        systemDetails.add(line);
                    }
                }
            }
        }

        // Keep the most specific two findings to avoid overwhelming the user.
        if (findings.size() > 4) {
            findings = new ArrayList<>(findings.subList(0, 4));
        }
        String errorSummary = description != null ? description : (findings.isEmpty() ? null : findings.get(0).title());
        return new Report(findings, description, crashName, systemDetails, analyzedFile, errorSummary);
    }

    private static String lineAt(String text, int index) {
        int start = text.lastIndexOf('\n', index) + 1;
        int end = text.indexOf('\n', index);
        if (end < 0) {
            end = text.length();
        }
        return stripAnsi(text.substring(start, end)).trim();
    }

    public static String stripAnsi(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("\u001B\\[[;\\d?]*[ -/]*[@-~]", "")
                .replaceAll("\u00A7[0-9a-fk-orA-FK-OR]", "");
    }

    /** Finds the newest crash report inside a game directory. */
    public static Path latestCrashReport(Path gameDir) {
        Path dir = gameDir.resolve("crash-reports");
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .max((a, b) -> Long.compare(a.toFile().lastModified(), b.toFile().lastModified()))
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    public static Map<String, String> knownFixes() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Java 版本过低", "切换到更高版本的 Java");
        map.put("内存不足", "提高最大内存");
        map.put("图形环境初始化失败", "更新显卡驱动");
        return map;
    }

    public static String severityRank(String severity) {
        return "严重".equals(severity) ? "严重" : "警告";
    }

    public static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
