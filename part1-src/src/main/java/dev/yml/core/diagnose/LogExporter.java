package dev.yml.core.diagnose;

import dev.yml.core.util.FileUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Converts a game .log (plus crash report) into a plain, shareable .txt report. */
public final class LogExporter {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private LogExporter() {
    }

    public static Path export(Path gameDir, Path logFile, Path crashReport, LogAnalyzer.Report report) {
        Path dir = gameDir.resolve("logs");
        FileUtil.ensureDir(dir);
        Path target = dir.resolve("yml-report-" + LocalDateTime.now().format(STAMP) + ".txt");

        StringBuilder out = new StringBuilder();
        out.append("Yumoo Minecraft Launcher · 游戏日志报告\n");
        out.append("生成时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append('\n');
        out.append("游戏目录: ").append(gameDir).append('\n');
        if (logFile != null) {
            out.append("日志来源: ").append(logFile).append('\n');
        }
        if (crashReport != null) {
            out.append("崩溃报告: ").append(crashReport.getFileName()).append('\n');
        }
        out.append('\n').append("================ 诊断结果 ================\n");
        if (report == null || report.findings().isEmpty()) {
            out.append("未能自动定位具体原因。请把本文件发送给会看日志的人，或附在求助帖中。\n");
        } else {
            for (LogAnalyzer.Finding finding : report.findings()) {
                out.append("[").append(finding.severity()).append("] ").append(finding.title()).append('\n');
                out.append("  ").append(finding.detail()).append('\n');
                for (String suggestion : finding.suggestions()) {
                    out.append("  - ").append(suggestion).append('\n');
                }
                for (String evidence : finding.evidence()) {
                    out.append("  > ").append(evidence).append('\n');
                }
                out.append('\n');
            }
        }
        if (report != null && report.crashDescription() != null) {
            out.append("崩溃描述: ").append(report.crashDescription()).append('\n');
        }
        if (report != null && !report.systemDetails().isEmpty()) {
            out.append("\n系统信息:\n");
            for (String line : report.systemDetails()) {
                out.append("  ").append(line).append('\n');
            }
        }

        out.append('\n').append("================ 游戏日志（已去除颜色控制符） ================\n");
        if (logFile != null && Files.isRegularFile(logFile)) {
            try {
                List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
                for (String line : lines) {
                    out.append(LogAnalyzer.stripAnsi(line)).append('\n');
                }
            } catch (IOException e) {
                out.append("(日志读取失败: ").append(e.getMessage()).append(")\n");
            }
        } else {
            out.append("(没有找到日志文件)\n");
        }

        if (crashReport != null && Files.isRegularFile(crashReport)) {
            out.append('\n').append("================ 崩溃报告原文 ================\n");
            try {
                out.append(Files.readString(crashReport, StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }
        FileUtil.writeString(target, out.toString());
        return target;
    }
}
