package dev.yml.ui.page;

import dev.yml.core.config.InstanceSettings;
import dev.yml.core.config.Settings;
import dev.yml.core.java.JavaRuntime;
import dev.yml.core.net.Mirror;
import dev.yml.ui.AppContext;
import dev.yml.ui.Page;
import dev.yml.ui.Ui;
import dev.yml.ui.UiHost;
import dev.yml.ui.Util;
import dev.yml.ui.component.Icon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Global settings page. */
public final class SettingsPage implements Page {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane view = new BorderPane();
    private final VBox sections = new VBox(14);

    private final TextField mcRootField = new TextField();
    private final ComboBox<String> sourceBox = new ComboBox<>();
    private final ComboBox<Integer> threadsBox = new ComboBox<>();
    private final TextField memoryField = new TextField();
    private final javafx.scene.control.ToggleGroup javaModeGroup = new javafx.scene.control.ToggleGroup();
    private final javafx.scene.control.RadioButton autoJava = new javafx.scene.control.RadioButton(
            "自动选择（按版本推荐）");
    private final javafx.scene.control.RadioButton manualJava = new javafx.scene.control.RadioButton("手动选择");
    private final VBox javaList = new VBox(3);
    private final List<JavaRuntime> javaRuntimes = new ArrayList<>();
    private String selectedJavaPath = "";
    private boolean loadingValues;
    private boolean settingsDirty;
    private boolean watcherInstalled;
    private javafx.animation.PauseTransition settingsToast;
    private final TextField curseKeyField = new TextField();
    private final TextField globalJvmArgsField = new TextField();
    private final TextField nativePathField = new TextField();
    private final CheckBox isolatedDefault = new CheckBox("默认启用版本隔离");
    private final CheckBox hideAfterLaunch = new CheckBox("启动游戏后隐藏启动器");
    private final CheckBox closeAfterLaunch = new CheckBox("启动游戏后关闭启动器");
    private final CheckBox showLog = new CheckBox("启动时显示游戏日志窗口");
    private final CheckBox autoAnalyze = new CheckBox("游戏异常退出后自动分析日志");
    private final CheckBox noDefaultJvmArgs = new CheckBox("不添加默认 JVM 参数（-Xms / -Xmx / 编码）");
    private final CheckBox noJvmTuning = new CheckBox("不自动添加 JVM 优化参数（G1GC / 并行引用处理）");
    private final CheckBox noJavaCompatCheck = new CheckBox("不检查 Java 与游戏版本兼容性");
    private final ComboBox<dev.yml.core.i18n.Language.Entry> languageBox = new ComboBox<>();
    private final CheckBox syncLanguage = new CheckBox(
            "将启动器语言同步到游戏实例（启动时写入 options.txt 的 lang）");
    private final ComboBox<String> themeModeBox = new ComboBox<>();
    private final javafx.scene.control.ColorPicker themeColorPicker =
            new javafx.scene.control.ColorPicker(javafx.scene.paint.Color.web("#1b1b22"));

    private final Slider dimSlider = new Slider(0, 0.92, 0.55);
    private final Slider blurSlider = new Slider(0, 24, 0);
    private final Label dimValue = Ui.label("55%", "tiny", "faint");
    private final Label blurValue = Ui.label("0 px", "tiny", "faint");
    private final Button modeDefault = new Button("深色纯色");
    private final Button modeImage = new Button("本地图片");
    private final Button modeWallpaper = new Button("桌面壁纸");
    private final Label backgroundHint = Ui.label("", "tiny", "faint");

    private final List<String> javaPaths = new ArrayList<>();
    private String backgroundMode;

    public SettingsPage(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
        loadValues();
    }

    private void build() {
        view.setPadding(new Insets(18, 20, 14, 20));
        HBox header = Ui.row(10, Ui.h1("设置"), Ui.small("全局启动器设置，保存后立即生效"));
        header.setAlignment(Pos.CENTER_LEFT);

        sections.setPadding(new Insets(14, 4, 20, 0));
        ScrollPane scroll = new ScrollPane(sections);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Button save = new Button("保存设置");
        save.getStyleClass().addAll("btn", "btn-primary");
        save.setOnAction(event -> save());
        Button exportSettings = new Button("导出设置");
        exportSettings.getStyleClass().add("btn");
        exportSettings.setOnAction(event -> exportSettings());
        Button importSettings = new Button("导入设置");
        importSettings.getStyleClass().add("btn");
        importSettings.setOnAction(event -> importSettings());
        Button dataFolder = new Button("打开数据目录");
        dataFolder.getStyleClass().add("btn");
        dataFolder.setOnAction(event -> Util.openFolder(context.dataRoot));
        HBox footer = new HBox(8, Ui.spacer(), importSettings, exportSettings, dataFolder, save);
        footer.setPadding(new Insets(8, 4, 0, 0));

        view.setTop(new VBox(12, header));
        view.setCenter(scroll);
        BorderPane.setMargin(scroll, new Insets(12, 0, 0, 0));
        view.setBottom(footer);
        buildSections();
    }

    private void buildSections() {
        // ------------------------------------------------------------ look
        modeDefault.getStyleClass().add("tab");
        modeImage.getStyleClass().add("tab");
        modeWallpaper.getStyleClass().add("tab");
        modeDefault.setOnAction(event -> switchBackgroundMode("DEFAULT"));
        modeImage.setOnAction(event -> switchBackgroundMode("IMAGE"));
        modeWallpaper.setOnAction(event -> switchBackgroundMode("WALLPAPER"));
        HBox modes = new HBox(6, modeDefault, modeImage, modeWallpaper);
        modes.setAlignment(Pos.CENTER_LEFT);

        Button pick = new Button("选择本地图片");
        pick.getStyleClass().addAll("btn", "btn-small");
        pick.setGraphic(Icon.of(Icon.FOLDER, 13, Ui.TEXT));
        pick.setOnAction(event -> pickImage());
        Button sync = new Button("同步桌面壁纸");
        sync.getStyleClass().addAll("btn", "btn-small");
        sync.setGraphic(Icon.of(Icon.REFRESH, 13, Ui.TEXT));
        sync.setOnAction(event -> syncWallpaper());

        dimSlider.setPrefWidth(220);
        blurSlider.setPrefWidth(220);
        dimSlider.valueProperty().addListener((obs, old, value) -> {
            dimValue.setText(Math.round(value.doubleValue() * 100) + "%");
            context.settings.backgroundDim = value.doubleValue();
            host.refreshBackground();
        });
        blurSlider.valueProperty().addListener((obs, old, value) -> {
            blurValue.setText(Math.round(value.doubleValue()) + " px");
            context.settings.backgroundBlur = value.doubleValue();
            host.refreshBackground();
        });

        languageBox.getItems().setAll(dev.yml.core.i18n.Language.all());
        languageBox.setVisibleRowCount(18);
        languageBox.setPrefWidth(430);
        languageBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(dev.yml.core.i18n.Language.Entry entry) {
                if (entry == null) {
                    return "";
                }
                return entry.name() + " · " + entry.code() + (entry.easterEgg() ? "  ✦" : "");
            }

            @Override
            public dev.yml.core.i18n.Language.Entry fromString(String string) {
                return null;
            }
        });
        syncLanguage.getStyleClass().add("check-box");
        sections.getChildren().add(section("语言",
                "默认简体中文；切换后界面立即生效。勾选同步后，启动游戏时会把所选语言写入实例的 options.txt。",
                row("启动器语言", languageBox),
                row("", syncLanguage)));

        themeModeBox.getItems().setAll("黑色（默认）", "自动（取背景主色）", "自定义");
        themeModeBox.setPrefWidth(190);
        themeModeBox.setOnAction(event ->
                themeColorPicker.setDisable(!"自定义".equals(themeModeBox.getValue())));
        sections.getChildren().add(section("外观与背景",
                "支持图片与 mp4/webm 视频壁纸；选择桌面壁纸后会跟随系统切换自动刷新。"
                        + "Wallpaper Engine 场景壁纸（scene.pkg）只能同步静态帧。",
                row("主题色", HBoxSlot(themeModeBox, themeColorPicker,
                        Ui.label("自动模式读取背景图主色", "tiny", "faint"))),
                row("背景", modes),
                row("", HBoxSlot(pick, sync, backgroundHint)),
                row("暗化", HBoxSlot(dimSlider, dimValue)),
                row("模糊", HBoxSlot(blurSlider, blurValue))));

        // ------------------------------------------------------------ game
        mcRootField.setPrefWidth(340);
        Button browse = new Button("选择已有 .minecraft");
        browse.getStyleClass().addAll("btn", "btn-small");
        browse.setOnAction(event -> chooseMcRoot());
        Button portableMc = new Button("使用启动器内 .minecraft");
        portableMc.getStyleClass().addAll("btn", "btn-small");
        portableMc.setOnAction(event -> applyMcRoot(dev.yml.core.AppPaths.portableMcRoot()));
        Button chooseData = new Button("选择已有数据目录");
        chooseData.getStyleClass().addAll("btn", "btn-small");
        chooseData.setOnAction(event -> chooseDataRoot());
        Button portableData = new Button("使用启动器目录");
        portableData.getStyleClass().addAll("btn", "btn-small");
        portableData.setOnAction(event -> usePortableData());
        isolatedDefault.getStyleClass().add("check-box");
        sections.getChildren().add(section("游戏",
                "首次启动会在启动器旁创建 .minecraft 与 YM Launcher；也可选择已有文件夹。",
                row("游戏目录", HBoxSlot(mcRootField, browse, portableMc)),
                row("数据目录", HBoxSlot(Ui.small(context.dataRoot.toString()), chooseData, portableData)),
                row("", isolatedDefault)));

        // ---------------------------------------------------------- download
        for (Mirror.Source source : Mirror.Source.values()) {
            sourceBox.getItems().add(source.label());
        }
        sourceBox.setPrefWidth(260);
        Label sourceHint = Ui.label("", "tiny", "faint");
        sourceBox.valueProperty().addListener((obs, old, value) -> {
            Mirror.Source source = selectedSource();
            sourceHint.setText(source == null ? "" : source.description());
        });
        threadsBox.getItems().setAll(4, 8, 16, 32);
        threadsBox.setPrefWidth(100);
        sections.getChildren().add(section("下载",
                "可切换官方源与国内外镜像；批量下载失败会自动尝试备用源。",
                row("下载源", new VBox(4, sourceBox, sourceHint)),
                row("并行线程", threadsBox),
                row("CurseForge", new VBox(4, curseKeyField,
                        Ui.label("用于“地图”下载；免费 Key 可在 console.curseforge.com 申请",
                                "tiny", "faint")))));

        // ----------------------------------------------------------- launch
        memoryField.setPrefWidth(110);
        memoryField.textProperty().addListener((obs, old, value) -> {
            if (!value.matches("\\d{0,6}")) {
                memoryField.setText(old);
            }
        });
        javaModeGroup.getToggles().addAll(autoJava, manualJava);
        autoJava.getStyleClass().add("check-box");
        manualJava.getStyleClass().add("check-box");
        autoJava.setOnAction(event -> refreshJavaSelection());
        manualJava.setOnAction(event -> refreshJavaSelection());
        Button rescanJava = new Button("重新检测");
        rescanJava.getStyleClass().addAll("btn", "btn-small");
        rescanJava.setOnAction(event -> loadJavaChoices());
        Button browseJava = new Button("浏览 java.exe");
        browseJava.getStyleClass().addAll("btn", "btn-small");
        browseJava.setOnAction(event -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("选择 Java 可执行文件");
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                    "Java 可执行文件", "java.exe", "javaw.exe", "java"));
            java.io.File file = chooser.showOpenDialog(view.getScene() == null ? null
                    : view.getScene().getWindow());
            if (file == null) {
                return;
            }
            JavaRuntime runtime = JavaRuntime.probe(file.toPath(), "手动");
            if (runtime == null) {
                dev.yml.ui.Util.error(view.getScene() == null ? null : view.getScene().getWindow(),
                        "无法识别 Java", "该文件不是可用的 Java 可执行文件。");
                return;
            }
            boolean exists = javaRuntimes.stream()
                    .anyMatch(r -> r.executable.toString().equals(runtime.executable.toString()));
            if (!exists) {
                javaRuntimes.add(runtime);
            }
            selectedJavaPath = runtime.executable.toString();
            manualJava.setSelected(true);
            refreshJavaSelection();
            host.toast("已选择 " + runtime.label() + " · " + runtime.arch);
        });
        javafx.scene.control.ScrollPane javaScroll = new javafx.scene.control.ScrollPane(javaList);
        javaScroll.setFitToWidth(true);
        javaScroll.setPrefHeight(210);
        javaScroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        javaScroll.setStyle("-fx-background-color: transparent;");
        VBox javaCard = new VBox(6, javaScroll, new HBox(8, rescanJava, browseJava,
                Ui.label("点击列表中的 Java 即可切换为手动选择", "tiny", "faint")));
        hideAfterLaunch.getStyleClass().add("check-box");
        closeAfterLaunch.getStyleClass().add("check-box");
        showLog.getStyleClass().add("check-box");
        autoAnalyze.getStyleClass().add("check-box");
        noDefaultJvmArgs.getStyleClass().add("check-box");
        noJvmTuning.getStyleClass().add("check-box");
        noJavaCompatCheck.getStyleClass().add("check-box");
        sections.getChildren().add(section("启动",
                "新实例默认使用按版本推荐的内存；关闭默认 JVM 参数后，内存与编码参数不再由启动器写入。",
                row("默认内存（参考）", HBoxSlot(memoryField, Ui.label("MB", "small", "muted"))),
                row("Java 选择", new VBox(6, autoJava, manualJava)),
                row("Java 列表", javaCard),
                row("全局 JVM 参数", jvmArgsRow()),
                row("本地库路径", nativePathRow()),
                row("JVM 行为", new VBox(6, noDefaultJvmArgs, noJvmTuning, noJavaCompatCheck)),
                row("", new VBox(6, hideAfterLaunch, closeAfterLaunch, autoAnalyze))));

        // ------------------------------------------------------------ about
        Button openLog = new Button("打开日志窗口");
        openLog.getStyleClass().addAll("btn", "btn-small");
        openLog.setOnAction(event -> host.openLog());
        sections.getChildren().add(section("关于",
                "Yumoo Minecraft Launcher (YML) 1.0.0 · JavaFX · 支持离线账户、版本下载与模组加载器安装",
                row("运行环境", Ui.small("Java " + System.getProperty("java.version")
                        + " · " + System.getProperty("os.name"))),
                row("许可证", Ui.small("GPLv3 · 正式版发布后将在 GitHub 开源，可自行编译复现")),
                row("文件校验", Ui.small("官方下载页提供 SHA256SUMS.txt；杀毒误报请先核对哈希")),
                row("公告源", announcementFeedField()),
                row("", HBoxSlot(openLog))));
    }

    private static String toHex(javafx.scene.paint.Color color) {        if (color == null) {
            return "#1b1b22";
        }
        return String.format(java.util.Locale.ROOT, "#%02x%02x%02x",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    private HBox announcementFeedField() {
        TextField field = new TextField(context.settings.announcementFeed == null
                ? "" : context.settings.announcementFeed);
        field.setPromptText("留空使用在线公告源；可填自定义 URL 或本地 json 路径");
        field.setPrefWidth(420);
        field.textProperty().addListener((obs, old, value) -> {
            context.settings.announcementFeed = value == null ? "" : value.trim();
        });
        return new HBox(8, field);
    }

    private HBox HBoxSlot(Node... nodes) {        HBox box = new HBox(8, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private VBox section(String title, String subtitle, Node... rows) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14, 16, 14, 16));
        card.getChildren().addAll(Ui.h3(title), Ui.label(subtitle, "tiny", "faint"));
        card.getChildren().addAll(rows);
        return card;
    }

    private HBox row(String label, Node control) {
        Label name = Ui.label(label, "small", "muted");
        name.setMinWidth(110);
        name.setPrefWidth(110);
        HBox box = new HBox(12, name, control);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        return box;
    }

    private void exportSettings() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("导出启动器设置");
        chooser.setInitialFileName("YML-settings.ylm");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                "YML 设置 (*.ylm)", "*.ylm", "*.YLM"));
        java.io.File file = chooser.showSaveDialog(view.getScene() == null ? null
                : view.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            dev.yml.core.config.SettingsIO.export(file.toPath(), context.settings);
            Util.info(host.window(), "设置已导出",
                    file.getAbsolutePath() + "\n\n只包含与默认值不同的设置项。");
        } catch (Exception e) {
            Util.error(host.window(), "导出失败", e.getMessage());
        }
    }

    private void importSettings() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("导入启动器设置");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                "YML 设置 (*.ylm)", "*.ylm", "*.YLM"));
        java.io.File file = chooser.showOpenDialog(view.getScene() == null ? null
                : view.getScene().getWindow());
        if (file == null) {
            return;
        }
        String oldMcRoot = context.settings.mcRoot;
        try {
            int applied = dev.yml.core.config.SettingsIO.apply(file.toPath(), context.settings);
            context.settings.save();
            dev.yml.ui.I18n.setLanguage(context.settings.language);
            host.applyLanguage();
            dev.yml.ui.ThemeManager.apply(view.getScene(), context.settings);
            if (!java.util.Objects.equals(oldMcRoot, context.settings.mcRoot)) {
                context.changeMcRoot(Path.of(context.settings.mcRoot));
            }
            host.refreshBackground();
            host.refreshAll();
            loadValues();
            Util.info(host.window(), "设置已导入",
                    "已应用 " + applied + " 项设置。\n部分设置可能需要重启启动器后完全生效。");
        } catch (Exception e) {
            Util.error(host.window(), "导入失败", e.getMessage());
        }
    }

    private void loadValues() {
        Settings settings = context.settings;
        mcRootField.setText(settings.mcRoot);
        for (int i = 0; i < Mirror.Source.values().length; i++) {
            if (Mirror.Source.values()[i] == settings.source()) {
                sourceBox.getSelectionModel().select(i);
                break;
            }
        }
        threadsBox.getSelectionModel().select(Integer.valueOf(settings.threads));
        curseKeyField.setText(settings.curseForgeApiKey);
        curseKeyField.setPromptText("CurseForge API Key（可选）");
        memoryField.setText(String.valueOf(settings.defaultMemoryMb));
        isolatedDefault.setSelected(settings.isolatedByDefault);
        hideAfterLaunch.setSelected(settings.hideAfterLaunch);
        closeAfterLaunch.setSelected(settings.closeAfterLaunch);
        showLog.setSelected(settings.showGameLog);
        autoAnalyze.setSelected(settings.autoAnalyzeCrash);
        dev.yml.core.i18n.Language.Entry languageEntry =
                dev.yml.core.i18n.Language.byCode(settings.language);
        languageBox.getSelectionModel().select(languageEntry);
        syncLanguage.setSelected(settings.syncLanguageToGame);
        globalJvmArgsField.setText(settings.globalJvmArgs == null ? "" : settings.globalJvmArgs);
        nativePathField.setText(settings.nativeLibraryPath == null ? "" : settings.nativeLibraryPath);
        noDefaultJvmArgs.setSelected(settings.noDefaultJvmArgs);
        noJvmTuning.setSelected(settings.noJvmTuning);
        noJavaCompatCheck.setSelected(settings.noJavaCompatCheck);
        themeModeBox.getSelectionModel().select(switch (settings.themeMode == null ? "BLACK"
                : settings.themeMode) {
            case "AUTO" -> 1;
            case "CUSTOM" -> 2;
            default -> 0;
        });
        try {
            themeColorPicker.setValue(javafx.scene.paint.Color.web(
                    settings.themeColor == null ? "#1b1b22" : settings.themeColor));
        } catch (Exception ignored) {
            themeColorPicker.setValue(javafx.scene.paint.Color.web("#1b1b22"));
        }
        themeColorPicker.setDisable(!"CUSTOM".equals(themeModeBox.getValue()));
        dimSlider.setValue(settings.backgroundDim);
        blurSlider.setValue(settings.backgroundBlur);
        dimValue.setText(Math.round(settings.backgroundDim * 100) + "%");
        blurValue.setText(Math.round(settings.backgroundBlur) + " px");
        backgroundMode = settings.backgroundMode;
        refreshModeButtons();
        if (settings.backgroundImage != null && !settings.backgroundImage.isBlank()) {
            backgroundHint.setText(Files.isRegularFile(Path.of(settings.backgroundImage))
                    ? Path.of(settings.backgroundImage).getFileName().toString() : "图片文件缺失");
        }
        loadJavaChoices();
    }

    private HBox jvmArgsRow() {
        globalJvmArgsField.setPrefWidth(430);
        globalJvmArgsField.setPromptText("例如 -XX:+UseZGC -XX:SoftMaxHeapSize=2G");
        return new HBox(8, globalJvmArgsField, Ui.label("空格分隔，追加到所有实例", "tiny", "faint"));
    }

    private HBox nativePathRow() {
        nativePathField.setPrefWidth(360);
        nativePathField.setPromptText("留空使用启动器解压的 natives；M1 等设备可手动指定");
        Button browse = new Button("浏览");
        browse.getStyleClass().addAll("btn", "btn-small");
        browse.setOnAction(event -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("选择本地库目录");
            java.io.File dir = chooser.showDialog(view.getScene() == null ? null
                    : view.getScene().getWindow());
            if (dir != null) {
                nativePathField.setText(dir.getAbsolutePath());
            }
        });
        return new HBox(8, nativePathField, browse);
    }

    private void loadJavaChoices() {
        javaList.getChildren().setAll(Ui.small("正在检测 Java..."));
        context.io.submit(() -> {
            List<JavaRuntime> runtimes = context.javaManager.refresh();
            javafx.application.Platform.runLater(() -> {
                javaRuntimes.clear();
                javaRuntimes.addAll(runtimes);
                String selected = context.settings.defaultJavaPath == null ? "" : context.settings.defaultJavaPath;
                selectedJavaPath = selected;
                if (selected.isBlank()) {
                    autoJava.setSelected(true);
                } else {
                    manualJava.setSelected(true);
                }
                rebuildJavaList();
                refreshJavaSelection();
            });
        });
    }

    private void rebuildJavaList() {
        javaList.getChildren().clear();
        if (javaRuntimes.isEmpty()) {
            javaList.getChildren().add(Ui.small("没有检测到 Java，可在下载页安装"));
            return;
        }
        for (JavaRuntime runtime : javaRuntimes) {
            boolean selected = !selectedJavaPath.isBlank()
                    && runtime.executable.toString().equals(selectedJavaPath);
            HBox row = new HBox(10);
            row.getStyleClass().add(selected ? "version-row-selected" : "row-hover");
            row.setPadding(new Insets(7, 10, 7, 10));
            row.setAlignment(Pos.CENTER_LEFT);
            Label name = Ui.label("Java " + runtime.major + " · " + runtime.vendor, "small");
            name.setMinWidth(210);
            Label detail = Ui.label(runtime.version + " · " + runtime.arch + " · "
                    + (runtime.javac ? "JDK" : "JRE"), "tiny", "faint");
            Label path = Ui.label(runtime.executable.toString(), "tiny", "faint");
            VBox info = new VBox(2, new HBox(8, name, detail), path);
            HBox.setHgrow(info, Priority.ALWAYS);
            row.getChildren().add(info);
            row.setOnMouseClicked(event -> {
                selectedJavaPath = runtime.executable.toString();
                manualJava.setSelected(true);
                rebuildJavaList();
                refreshJavaSelection();
            });
            javaList.getChildren().add(row);
        }
    }

    private void refreshJavaSelection() {
        boolean manual = manualJava.isSelected();
        javaList.setOpacity(manual ? 1.0 : 0.65);
        if (!manual) {
            selectedJavaPath = "";
        }
        rebuildJavaList();
    }

    private void refreshModeButtons() {
        for (Button button : new Button[]{modeDefault, modeImage, modeWallpaper}) {
            button.getStyleClass().remove("tab-active");
        }
        switch (backgroundMode) {
            case "IMAGE" -> modeImage.getStyleClass().add("tab-active");
            case "WALLPAPER" -> modeWallpaper.getStyleClass().add("tab-active");
            default -> modeDefault.getStyleClass().add("tab-active");
        }
    }

    private void switchBackgroundMode(String mode) {
        if ("IMAGE".equals(mode) && (context.settings.backgroundImage == null
                || context.settings.backgroundImage.isBlank())) {
            pickImage();
            return;
        }
        if ("WALLPAPER".equals(mode)) {
            syncWallpaper();
            return;
        }
        backgroundMode = mode;
        context.settings.backgroundMode = mode;
        refreshModeButtons();
        context.settings.backgroundDim = dimSlider.getValue();
        context.settings.backgroundBlur = blurSlider.getValue();
        host.refreshBackground();
    }

    private void pickImage() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("选择背景图片");
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("图片或视频",
                        "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.mp4", "*.webm"),
                new javafx.stage.FileChooser.ExtensionFilter("所有文件", "*.*"));
        File file = chooser.showOpenDialog(host.window());
        if (file == null) {
            return;
        }
        if (!host.importBackgroundImage(file.toPath())) {
            Util.error(host.window(), "无法设置背景", "该图片无法读取或复制失败");
            return;
        }
        backgroundMode = "IMAGE";
        refreshModeButtons();
        backgroundHint.setText(file.getName());
        host.refreshBackground();
    }

    private void syncWallpaper() {
        host.runTask("同步桌面壁纸", progress -> {
            progress.note("读取当前桌面壁纸");
            Path path;
            try {
                path = host.syncWallpaper();
            } catch (java.io.IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
            javafx.application.Platform.runLater(() -> {
                backgroundMode = "WALLPAPER";
                refreshModeButtons();
                backgroundHint.setText(path.getFileName().toString());
                host.refreshBackground();
                host.toast("已同步桌面壁纸");
            });
        }, null);
    }

    private void chooseDataRoot() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("选择已有启动器数据目录（YM Launcher）");
        java.io.File dir = chooser.showDialog(view.getScene() == null ? null
                : view.getScene().getWindow());
        if (dir == null) {
            return;
        }
        Path dataRoot = dir.toPath();
        if (!Files.isRegularFile(dataRoot.resolve("config.json"))) {
            boolean ok = Util.confirm(host.window(), "可能不是启动器数据目录",
                    "所选目录中没有 config.json：\n" + dataRoot
                            + "\n\n仍要把它作为数据目录吗？", "仍然使用", "取消");
            if (!ok) {
                return;
            }
        }
        dev.yml.core.AppPaths.writePointer(dataRoot, Path.of(mcRootField.getText().trim()));
        Util.info(host.window(), "数据目录已记录",
                "下次启动将使用：\n" + dataRoot + "\n\n请重启启动器使设置生效。");
    }

    private void usePortableData() {
        dev.yml.core.AppPaths.writePointer(dev.yml.core.AppPaths.portableDataRoot(),
                dev.yml.core.AppPaths.portableMcRoot());
        Util.info(host.window(), "已切换到启动器目录",
                "数据目录：" + dev.yml.core.AppPaths.portableDataRoot()
                        + "\n游戏目录：" + dev.yml.core.AppPaths.portableMcRoot()
                        + "\n\n请重启启动器，首次会在启动器旁创建这两个文件夹。");
    }

    private void chooseMcRoot() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择 .minecraft 目录");
        File current = new File(context.settings.mcRoot);
        if (current.isDirectory()) {
            chooser.setInitialDirectory(current);
        }
        File file = chooser.showDialog(host.window());
        if (file == null) {
            return;
        }
        Path chosen = file.toPath();
        // 用户选到父目录时自动进入 .minecraft。
        if (!Files.isDirectory(chosen.resolve("versions"))
                && Files.isDirectory(chosen.resolve(".minecraft"))) {
            chosen = chosen.resolve(".minecraft");
        }
        applyMcRoot(chosen);
    }

    private void applyMcRoot(Path chosen) {
        mcRootField.setText(chosen.toString());
        context.changeMcRoot(chosen);
        context.settings.save();
        host.refreshAll();
        if (!Files.isDirectory(chosen.resolve("versions"))) {
            Util.info(host.window(), "游戏目录已切换",
                    "已切换到：\n" + chosen + "\n\n该目录下没有 versions 文件夹，可能不是 .minecraft 目录。");
        } else {
            host.toast("已读取游戏目录：" + chosen);
        }
    }

    private void save() {
        Settings settings = context.settings;
        String previousLanguage = settings.language;
        String mcRoot = mcRootField.getText().trim();
        if (!mcRoot.isBlank() && !Files.isDirectory(Path.of(mcRoot))) {
            Util.error(host.window(), "游戏目录无效", "目录不存在: " + mcRoot);
            return;
        }
        boolean rootChanged = !mcRoot.equals(settings.mcRoot);
        if (!mcRoot.isBlank()) {
            settings.mcRoot = mcRoot;
        }
        settings.downloadSource = selectedSource() == null ? "AUTO" : selectedSource().id();
        Integer threads = threadsBox.getSelectionModel().getSelectedItem();
        settings.threads = threads == null ? 16 : threads;
        settings.defaultMemoryMb = Math.max(256, parseInt(memoryField.getText(), settings.defaultMemoryMb));
        settings.isolatedByDefault = isolatedDefault.isSelected();
        settings.hideAfterLaunch = hideAfterLaunch.isSelected();
        settings.closeAfterLaunch = closeAfterLaunch.isSelected();
        settings.showGameLog = showLog.isSelected();
        settings.autoAnalyzeCrash = autoAnalyze.isSelected();
        settings.globalJvmArgs = globalJvmArgsField.getText() == null ? "" : globalJvmArgsField.getText().trim();
        settings.nativeLibraryPath = nativePathField.getText() == null ? "" : nativePathField.getText().trim();
        settings.noDefaultJvmArgs = noDefaultJvmArgs.isSelected();
        settings.noJvmTuning = noJvmTuning.isSelected();
        settings.noJavaCompatCheck = noJavaCompatCheck.isSelected();
        settings.curseForgeApiKey = curseKeyField.getText() == null ? "" : curseKeyField.getText().trim();
        settings.backgroundMode = backgroundMode;
        settings.backgroundDim = dimSlider.getValue();
        settings.backgroundBlur = blurSlider.getValue();
        settings.defaultJavaPath = manualJava.isSelected() ? selectedJavaPath : "";
        if (languageBox.getValue() != null) {
            settings.language = languageBox.getValue().code();
        }
        settings.syncLanguageToGame = syncLanguage.isSelected();
        settings.themeMode = switch (themeModeBox.getSelectionModel().getSelectedIndex()) {
            case 1 -> "AUTO";
            case 2 -> "CUSTOM";
            default -> "BLACK";
        };
        settings.themeColor = toHex(themeColorPicker.getValue());
        settings.save();
        dev.yml.ui.I18n.setLanguage(settings.language);
        host.applyLanguage();
        dev.yml.ui.ThemeManager.apply(view.getScene(), settings);
        if (rootChanged) {
            context.changeMcRoot(Path.of(settings.mcRoot));
        }
        host.refreshBackground();
        host.refreshAll();
        settingsDirty = false;
        if (settingsToast != null) {
            settingsToast.stop();
        }
        if (!java.util.Objects.equals(previousLanguage, settings.language)) {
            var language = dev.yml.core.i18n.Language.byCode(settings.language);
            host.toast("界面语言已切换为 " + (language == null ? settings.language : language.name()));
        } else {
            host.toast("设置已保存，部分设置重启启动器后生效");
        }
    }

    private void installChangeWatcher() {
        watch(view);
    }

    private void watch(javafx.scene.Node node) {
        if (node instanceof javafx.scene.control.Toggle toggle) {
            toggle.selectedProperty().addListener((obs, old, value) -> settingsChanged());
        } else if (node instanceof javafx.scene.control.ComboBox<?> box) {
            box.valueProperty().addListener((obs, old, value) -> settingsChanged());
        } else if (node instanceof javafx.scene.control.ColorPicker picker) {
            picker.valueProperty().addListener((obs, old, value) -> settingsChanged());
        } else if (node instanceof javafx.scene.control.Slider slider) {
            slider.valueProperty().addListener((obs, old, value) -> settingsChanged());
        } else if (node instanceof javafx.scene.control.TextField field) {
            field.textProperty().addListener((obs, old, value) -> settingsChanged());
        }
        if (node instanceof javafx.scene.control.ScrollPane scroll
                && scroll.getContent() != null) {
            watch(scroll.getContent());
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                watch(child);
            }
        }
    }

    private void settingsChanged() {
        if (loadingValues) {
            return;
        }
        settingsDirty = true;
        if (settingsToast == null) {
            settingsToast = new javafx.animation.PauseTransition(javafx.util.Duration.millis(650));
            settingsToast.setOnFinished(event -> {
                if (settingsDirty) {
                    host.toast("设置已修改，点击「保存」生效");
                }
            });
        }
        settingsToast.playFromStart();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private Mirror.Source selectedSource() {
        int index = sourceBox.getSelectionModel().getSelectedIndex();
        return index < 0 || index >= Mirror.Source.values().length ? Mirror.Source.AUTO
                : Mirror.Source.values()[index];
    }

    @Override
    public Node view() {
        return view;
    }

    @Override
    public void onShow() {
        loadingValues = true;
        try {
            loadJavaChoices();
        } finally {
            loadingValues = false;
        }
        if (!watcherInstalled) {
            watcherInstalled = true;
            installChangeWatcher();
        }
    }
}
