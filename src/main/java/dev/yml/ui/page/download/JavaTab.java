package dev.yml.ui.page.download;

import dev.yml.core.java.JavaManager;
import dev.yml.core.java.JavaRuntime;
import dev.yml.ui.AppContext;
import dev.yml.ui.Ui;
import dev.yml.ui.UiHost;
import dev.yml.ui.Util;
import dev.yml.ui.component.Icon;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Java runtime manager: detected runtimes, Temurin LTS downloads and Mojang runtimes. */
public final class JavaTab implements DownloadTab {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane root = new BorderPane();
    private final VBox installedBox = new VBox(6);
    private final FlowPane commonBox = new FlowPane(12, 12);
    private final VBox mojangBox = new VBox(4);
    private final Label installedCount = Ui.label("", "tiny", "faint");
    private final Label optionsStatus = Ui.label("正在获取可用 Java 版本...", "tiny", "faint");
    private final javafx.scene.control.ComboBox<JavaManager.JavaSource> javaSourceBox =
            new javafx.scene.control.ComboBox<>();
    private boolean loaded;

    public JavaTab(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        VBox content = new VBox(18);
        content.setPadding(new Insets(0, 6, 12, 0));

        // ---------------------------------------------------------- installed
        HBox installedHeader = new HBox(8, Ui.h3("已安装的 Java"), installedCount, Ui.spacer());
        Button refresh = new Button("重新检测");
        refresh.getStyleClass().addAll("btn", "btn-small");
        refresh.setGraphic(Icon.of(Icon.REFRESH, 13, Ui.TEXT_DIM));
        refresh.setOnAction(event -> refreshInstalled(true));
        Button openFolder = new Button("打开运行时目录");
        openFolder.getStyleClass().addAll("btn", "btn-small");
        openFolder.setOnAction(event -> Util.openFolder(context.javaManager.runtimeDir()));
        installedHeader.getChildren().addAll(refresh, openFolder);

        VBox installedCard = new VBox(8, installedHeader, installedBox);
        installedCard.getStyleClass().add("card");
        installedCard.setPadding(new Insets(14, 16, 14, 16));

        // ------------------------------------------------------------- common
        javaSourceBox.getItems().setAll(JavaManager.JavaSource.values());
        javaSourceBox.getSelectionModel().select("TUNA".equalsIgnoreCase(context.settings.javaSource)
                ? JavaManager.JavaSource.TUNA : JavaManager.JavaSource.ADOPTIUM_API);
        javaSourceBox.valueProperty().addListener((obs, old, value) -> {
            if (value != null) {
                context.settings.javaSource = value.name();
                context.settings.save();
            }
        });
        HBox commonHeader = new HBox(8, Ui.h3("常用 Java 版本"), Ui.small("Eclipse Temurin · 免安装 JRE"),
                Ui.spacer(), Ui.label("下载源", "tiny", "faint"), javaSourceBox);
        commonBox.setPrefWrapLength(760);
        VBox commonCard = new VBox(10, commonHeader, commonBox);
        commonCard.getStyleClass().add("card");
        commonCard.setPadding(new Insets(14, 16, 14, 16));

        // ------------------------------------------------------------- mojang
        HBox mojangHeader = new HBox(8, Ui.h3("Mojang 官方运行时"),
                Ui.small("与游戏版本严格匹配，由官方清单提供"), Ui.spacer());
        VBox mojangCard = new VBox(10, mojangHeader, mojangBox);
        mojangCard.getStyleClass().add("card");
        mojangCard.setPadding(new Insets(14, 16, 14, 16));

        content.getChildren().addAll(installedCard, commonCard, mojangCard);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        root.setCenter(scroll);

        Label hint = Ui.label("提示：启动器会优先使用与游戏版本匹配的 Java；离线游玩只需 JRE。", "tiny", "faint");
        root.setBottom(hint);
        BorderPane.setMargin(hint, new Insets(6, 0, 0, 2));
    }

    @Override
    public Node view() {
        return root;
    }

    @Override
    public void onShow() {
        if (!loaded) {
            loaded = true;
            refreshInstalled(false);
            loadOptions();
        }
    }

    @Override
    public void refresh() {
        refreshInstalled(true);
        loadOptions();
    }

    private JavaManager.JavaSource selectedJavaSource() {
        JavaManager.JavaSource source = javaSourceBox.getSelectionModel().getSelectedItem();
        return source == null ? JavaManager.JavaSource.ADOPTIUM_API : source;
    }

    private void refreshInstalled(boolean reScan) {
        installedBox.getChildren().setAll(Ui.small("正在检测 Java..."));
        context.io.submit(() -> {
            List<JavaRuntime> list = reScan ? context.javaManager.refresh() : context.javaManager.installed();
            if (list.isEmpty()) {
                list = context.javaManager.refresh();
            }
            List<JavaRuntime> finalList = list;
            Platform.runLater(() -> renderInstalled(finalList));
        });
    }

    private void renderInstalled(List<JavaRuntime> list) {
        installedBox.getChildren().clear();
        installedCount.setText(list.size() + " 个");
        if (list.isEmpty()) {
            installedBox.getChildren().add(Ui.small("未检测到 Java，请下载一个常用版本"));
            return;
        }
        for (JavaRuntime runtime : list) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 10, 8, 10));
            row.getStyleClass().add("row-hover");

            Label major = Ui.label("Java " + runtime.major, "h3");
            major.setMinWidth(76);
            VBox info = new VBox(2,
                    Ui.label(runtime.vendor + " · " + runtime.version + " · " + runtime.arch, "small"),
                    Ui.label(runtime.executable.toString(), "tiny", "faint"));
            HBox.setHgrow(info, Priority.ALWAYS);

            Label badge = Ui.chip("启动器下载", "chip-accent");
            boolean managed = runtime.home.startsWith(context.javaManager.runtimeDir());
            if (!managed) {
                badge.setText(runtime.javac ? "本机 JDK" : "本机 JRE");
                badge.getStyleClass().setAll("chip");
            }

            Button use = new Button("设为默认");
            use.getStyleClass().addAll("btn", "btn-small");
            use.setOnAction(event -> {
                context.settings.defaultJavaPath = runtime.executable.toString();
                context.settings.save();
                host.toast("默认 Java 已设为 " + runtime.label());
            });
            Button reveal = new Button("打开目录");
            reveal.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            reveal.setOnAction(event -> Util.openFolder(runtime.home));
            row.getChildren().addAll(Icon.of(Icon.JAVA, 18, Ui.TEXT_DIM), major, info, badge, reveal, use);
            if (managed) {
                Button delete = new Button();
                delete.setGraphic(Icon.of(Icon.TRASH, 14, Ui.RED));
                delete.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
                delete.setOnAction(event -> {
                    if (Util.confirm(host.window(), "删除 Java", "确定删除 " + runtime.label() + " 吗？")) {
                        context.javaManager.delete(runtime);
                        refreshInstalled(false);
                    }
                });
                row.getChildren().add(delete);
            }
            installedBox.getChildren().add(row);
        }
    }

    private void loadOptions() {
        context.io.submit(() -> {
            List<JavaManager.AdoptiumOption> adoptium = context.javaManager.adoptiumOptions();
            List<JavaManager.MojangOption> mojang = context.javaManager.mojangOptions();
            Platform.runLater(() -> {
                renderCommon(adoptium);
                renderMojang(mojang);
                optionsStatus.setText("");
            });
        });
    }

    private void renderCommon(List<JavaManager.AdoptiumOption> options) {
        commonBox.getChildren().clear();
        for (JavaManager.AdoptiumOption option : options) {
            boolean installed = context.javaManager.installed().stream()
                    .anyMatch(r -> r.major == option.major() && r.home.startsWith(context.javaManager.runtimeDir()));
            VBox card = new VBox(4);
            card.getStyleClass().add("card-soft");
            card.setPadding(new Insets(12, 14, 12, 14));
            card.setPrefWidth(172);
            Label title = Ui.h2("Java " + option.major());
            Label tag = Ui.label(option.lts() ? "LTS · Temurin JRE" : "最新特性版 · Temurin JRE", "tiny", "faint");
            Label size = Ui.label("约 " + (option.major() >= 17 ? "50" : "40") + " MB", "tiny", "faint");
            Button download = new Button(installed ? "已安装" : "下载");
            download.getStyleClass().addAll("btn", "btn-small", installed ? "btn-ghost" : "btn-primary");
            download.setDisable(installed);
            download.setOnAction(event -> {
                download.setDisable(true);
                download.setText("下载中");
                host.runTask("下载 Java " + option.major(), progress ->
                        context.javaManager.installAdoptium(option.major(), selectedJavaSource(), progress), () -> {
                    host.toast("Java " + option.major() + " 安装完成");
                    refreshInstalled(false);
                    loadOptions();
                });
            });
            card.getChildren().addAll(title, tag, size, download);
            commonBox.getChildren().add(card);
        }
    }

    private void renderMojang(List<JavaManager.MojangOption> options) {
        mojangBox.getChildren().clear();
        if (options.isEmpty()) {
            mojangBox.getChildren().add(Ui.small("暂时无法获取 Mojang 运行时清单"));
            return;
        }
        for (JavaManager.MojangOption option : options) {
            int major = JavaRuntime.parseMajor(option.versionName());
            boolean installed = context.javaManager.installed().stream()
                    .anyMatch(r -> r.major == major && r.home.toString().contains("mojang-" + option.component()));
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(7, 4, 7, 4));
            Label name = Ui.label(option.label(), "small");
            name.setMinWidth(210);
            Label version = Ui.label(option.versionName(), "tiny", "faint");
            HBox.setHgrow(version, Priority.ALWAYS);
            Label released = Ui.label(option.released() == null || option.released().length() < 10 ? ""
                    : option.released().substring(0, 10), "tiny", "faint");
            Button download = new Button(installed ? "已安装" : "下载");
            download.getStyleClass().addAll("btn", "btn-small", installed ? "btn-ghost" : "");
            download.setDisable(installed);
            download.setOnAction(event -> {
                download.setDisable(true);
                download.setText("下载中");
                host.runTask("下载 " + option.component(), progress ->
                        context.javaManager.installMojang(option, progress), () -> {
                    host.toast(option.component() + " 安装完成");
                    refreshInstalled(false);
                    loadOptions();
                });
            });
            row.getChildren().addAll(Icon.of(Icon.JAVA, 14, Ui.TEXT_FAINT), name, version, released, download);
            mojangBox.getChildren().add(row);
        }
    }
}
