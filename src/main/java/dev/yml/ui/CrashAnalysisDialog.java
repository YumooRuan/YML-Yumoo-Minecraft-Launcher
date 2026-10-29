package dev.yml.ui;

import dev.yml.core.diagnose.LogAnalyzer;
import dev.yml.core.diagnose.LogExporter;
import dev.yml.ui.component.DialogShell;
import dev.yml.ui.component.Icon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.nio.file.Path;

/** Crash / error report with actionable suggestions and one-click log export. */
public final class CrashAnalysisDialog {

    private final DialogShell shell;
    private final Path gameDir;
    private final Path logFile;
    private final Path crashReport;
    private final LogAnalyzer.Report report;
    private Path exported;
    private final Label exportHint = Ui.label("", "tiny", "faint");

    public CrashAnalysisDialog(Window owner, String versionId, Path gameDir, Path logFile, Path crashReport,
                               LogAnalyzer.Report report, Path exported) {
        this.gameDir = gameDir;
        this.logFile = logFile;
        this.crashReport = crashReport;
        this.report = report;
        this.exported = exported;
        this.shell = new DialogShell(owner, "日志诊断 · " + versionId, 860, 720);
        build();
    }

    private void build() {
        VBox content = new VBox(14);
        content.setPadding(new Insets(0, 6, 8, 0));

        boolean severe = report.findings().stream().anyMatch(f -> "严重".equals(f.severity()));
        Color color = severe ? Ui.RED : (report.confident() ? Ui.YELLOW : Ui.TEXT_DIM);
        String headline = report.headline();
        Label title = Ui.h2(headline);
        Label sub = Ui.label(report.confident()
                ? "根据日志匹配到 " + report.findings().size() + " 条可疑原因，建议按下面的顺序排查。"
                : "日志中没有命中已知问题，已生成可直接分享的文本报告。", "small", "muted");
        sub.setWrapText(true);
        VBox headText = new VBox(4, title, sub);
        HBox head = new HBox(14, Icon.of(report.confident() ? Icon.INFO : Icon.TERMINAL, 34, color), headText);
        head.setAlignment(Pos.CENTER_LEFT);
        head.getStyleClass().add("card");
        head.setPadding(new Insets(16, 18, 16, 18));
        content.getChildren().add(head);

        if (report.crashDescription() != null && !report.crashDescription().isBlank()) {
            content.getChildren().add(card("崩溃描述", Ui.label(report.crashDescription(), "small"),
                    Ui.label("来自 " + (report.crashReportName() == null ? "崩溃报告" : report.crashReportName()),
                            "tiny", "faint")));
        }

        for (LogAnalyzer.Finding finding : report.findings()) {
            Color findingColor = "严重".equals(finding.severity()) ? Ui.RED : Ui.YELLOW;
            VBox card = new VBox(8);
            card.getStyleClass().add("card");
            card.setPadding(new Insets(14, 16, 14, 16));
            HBox titleRow = new HBox(8,
                    Ui.chip(finding.severity(), "严重".equals(finding.severity()) ? "chip-red" : "chip-orange"),
                    Ui.h3(finding.title()));
            titleRow.setAlignment(Pos.CENTER_LEFT);
            Label detail = Ui.label(finding.detail(), "small", "muted");
            detail.setWrapText(true);
            card.getChildren().addAll(titleRow, detail);
            if (!finding.suggestions().isEmpty()) {
                VBox suggestions = new VBox(3);
                suggestions.getChildren().add(Ui.label("建议", "tiny", "faint"));
                for (String suggestion : finding.suggestions()) {
                    suggestions.getChildren().add(Ui.label("· " + suggestion, "small"));
                }
                card.getChildren().add(suggestions);
            }
            if (!finding.evidence().isEmpty()) {
                VBox evidence = new VBox(2);
                evidence.getChildren().add(Ui.label("日志片段", "tiny", "faint"));
                for (String line : finding.evidence()) {
                    Label item = Ui.label(line, "tiny", "mono");
                    item.setStyle("-fx-text-fill: #8f8fa0;");
                    item.setWrapText(true);
                    evidence.getChildren().add(item);
                }
                card.getChildren().add(evidence);
            }
            content.getChildren().add(card);
        }

        if (!report.systemDetails().isEmpty()) {
            VBox details = new VBox(2);
            for (String line : report.systemDetails()) {
                details.getChildren().add(Ui.label(line, "tiny", "mono"));
            }
            content.getChildren().add(card("系统信息", details));
        }

        VBox source = new VBox(3,
                Ui.label("分析日志: " + (logFile == null ? "无" : logFile), "tiny", "faint"),
                (crashReport == null ? Ui.label("崩溃报告: 无", "tiny", "faint")
                        : Ui.label("崩溃报告: " + crashReport.getFileName(), "tiny", "faint")),
                exportHint);
        content.getChildren().add(source);
        if (exported != null) {
            exportHint.setText("已导出可分享文本: " + exported);
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        shell.setContent(scroll);

        Button openFolder = new Button("打开日志目录");
        openFolder.getStyleClass().add("btn");
        openFolder.setOnAction(event -> Util.openFolder(logFile == null ? gameDir.resolve("logs")
                : logFile.getParent()));

        Button copy = new Button("复制诊断");
        copy.getStyleClass().add("btn");
        copy.setOnAction(event -> {
            ClipboardContent clip = new ClipboardContent();
            clip.putString(toShareText());
            Clipboard.getSystemClipboard().setContent(clip);
            exportHint.setText("诊断结果已复制到剪贴板");
        });

        Button export = new Button(exported == null ? "导出为 .txt" : "重新导出 .txt");
        export.getStyleClass().add("btn");
        export.setOnAction(event -> {
            exported = LogExporter.export(gameDir, logFile, crashReport, report);
            exportHint.setText("已导出可分享文本: " + exported);
            export.setText("重新导出 .txt");
        });

        Button close = new Button("关闭");
        close.getStyleClass().addAll("btn", "btn-primary");
        close.setOnAction(event -> shell.close());
        shell.setFooter(openFolder, copy, export, Ui.spacer(), close);
    }

    private VBox card(String title, javafx.scene.Node... nodes) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card-soft");
        card.setPadding(new Insets(12, 14, 12, 14));
        card.getChildren().add(Ui.label(title, "section-title"));
        card.getChildren().addAll(nodes);
        return card;
    }

    private String toShareText() {
        StringBuilder sb = new StringBuilder();
        sb.append("日志诊断: ").append(report.headline()).append('\n');
        if (report.crashDescription() != null) {
            sb.append("崩溃描述: ").append(report.crashDescription()).append('\n');
        }
        for (LogAnalyzer.Finding finding : report.findings()) {
            sb.append("\n[").append(finding.severity()).append("] ").append(finding.title()).append('\n');
            sb.append("  ").append(finding.detail()).append('\n');
            for (String suggestion : finding.suggestions()) {
                sb.append("  - ").append(suggestion).append('\n');
            }
        }
        sb.append("\n日志: ").append(logFile).append('\n');
        return sb.toString();
    }

    public void show() {
        shell.showAndWait();
    }
}
