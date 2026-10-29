package dev.yml.ui;

import dev.yml.core.auth.Account;
import dev.yml.core.auth.AuthlibInjector;
import dev.yml.core.auth.MicrosoftAuth;
import dev.yml.core.config.InstanceSettings;
import dev.yml.core.diagnose.LogAnalyzer;
import dev.yml.core.diagnose.LogExporter;
import dev.yml.core.game.GameLauncher;
import dev.yml.core.game.LaunchOptions;
import dev.yml.core.game.LocalVersions;
import dev.yml.core.game.VersionInfo;
import dev.yml.core.java.JavaRuntime;
import dev.yml.core.util.Log;
import dev.yml.core.util.Progress;
import dev.yml.core.util.TaskCancelledException;
import dev.yml.ui.component.LogWindow;
import dev.yml.ui.component.Sidebar;
import dev.yml.ui.component.TaskOverlay;
import dev.yml.ui.component.TitleBar;
import dev.yml.ui.page.AccountsPage;
import dev.yml.ui.page.DownloadPage;
import dev.yml.ui.page.HomePage;
import dev.yml.ui.page.ModsPage;
import dev.yml.ui.page.SettingsPage;
import dev.yml.ui.page.VersionsPage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** The launcher's single main window. */
public final class MainWindow implements VersionsPage.Host, UiHost {

    private final Stage stage;
    private final AppContext context;
    private final StackPane content = new StackPane();
    private final TaskOverlay overlay = new TaskOverlay();
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Sidebar sidebar;
    private final Label statusLabel = Ui.label("就绪", "tiny", "muted");
    private final ProgressBar statusProgress = new ProgressBar(0);
    private final BackgroundManager background;

    private Process running;
    private LogWindow logWindow;
    private final java.util.List<String> pageOrder = java.util.List.of(
            "home", "download", "multiplayer", "keybinds", "announcements",
            "announcement-favorites", "mods", "resourcepacks", "saves", "servers",
            "shader-configs", "accounts", "admin-release", "admin-announcements", "settings");
    private String currentPage = "home";
    private boolean switching;
    private String pendingPage;
    private final boolean adminMode;
    private StackPane rootStack;
    private dev.yml.ui.component.UpdateToast updateToast;

    public MainWindow(Stage stage, AppContext context) {
        this(stage, context, false);
    }

    public MainWindow(Stage stage, AppContext context, boolean adminMode) {
        this.stage = stage;
        this.context = context;
        this.adminMode = adminMode;
        stage.getIcons().clear();
        for (int iconSize : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            stage.getIcons().add(TitleBar.appIcon(iconSize));
        }
        this.background = new BackgroundManager(context);
        this.sidebar = new Sidebar(context, new Sidebar.Listener() {
            @Override
            public void onNavigate(String page) {
                showPage(page);
            }

            @Override
            public void onAccountClick() {
                showPage("accounts");
            }

            @Override
            public void onLanguage(String code) {
                context.settings.language = code;
                context.settings.save();
                applyLanguage();
                var entry = dev.yml.core.i18n.Language.byCode(code);
                toast("语言已切换为 " + (entry == null ? code : entry.name()));
            }

            @Override
            public void onMoreLanguages() {
                showPage("settings");
            }
        }, adminMode);
        build();
    }

    private void build() {
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle(adminMode ? "Yumoo Minecraft Launcher · Admin" : "Yumoo Minecraft Launcher");

        BorderPane shell = new BorderPane();
        shell.setTop(new TitleBar(stage));
        shell.setLeft(sidebar);
        content.setStyle("-fx-background-color: rgba(13, 13, 18, 0.72);");
        shell.setCenter(content);
        shell.setBottom(statusBar());

        rootStack = new StackPane(background.view(), shell, overlay);
        rootStack.setStyle("-fx-background-color: #121216;");
        javafx.geometry.Rectangle2D visual = javafx.stage.Screen.getPrimary().getVisualBounds();
        Log.info("屏幕: bounds=%s visual=%s scale=%.2f", javafx.stage.Screen.getPrimary().getBounds(), visual,
                javafx.stage.Screen.getPrimary().getOutputScaleX());
        double maxWidth = Math.min(1280, visual.getWidth() - 60);
        double maxHeight = Math.min(860, visual.getHeight() - 60);
        double width = Math.max(980, Math.min(maxWidth, visual.getWidth() - 60));
        double height = Math.max(620, Math.min(maxHeight, visual.getHeight() - 60));
        if (context.settings.windowWidth >= 900 && context.settings.windowWidth <= maxWidth) {
            width = context.settings.windowWidth;
        }
        if (context.settings.windowHeight >= 600 && context.settings.windowHeight <= maxHeight) {
            height = context.settings.windowHeight;
        }
        Scene scene = new Scene(rootStack, width, height);
        var css = MainWindow.class.getResource("/ui/theme.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.setMinWidth(980);
        stage.setMinHeight(620);
        stage.setX(visual.getMinX() + (visual.getWidth() - width) / 2);
        stage.setY(visual.getMinY() + (visual.getHeight() - height) / 2);
        installWindowResize(scene);
        ThemeManager.apply(scene, context.settings);

        stage.setOnCloseRequest(event -> {
            context.settings.windowWidth = stage.getWidth();
            context.settings.windowHeight = stage.getHeight();
            context.settings.maximized = stage.isMaximized();
            context.settings.save();
            Platform.exit();
        });

        HomePage home = new HomePage(this, context);
        VersionsPage versions = new VersionsPage(this, context);
        pages.put("home", home);
        pages.put("versions", versions);
        pages.put("download", new DownloadPage(context, this));
        pages.put("keybinds", new dev.yml.ui.page.KeybindsPage(context, this));
        pages.put("announcements", new dev.yml.ui.page.AnnouncementsPage(context, this, false));
        pages.put("announcement-favorites", new dev.yml.ui.page.AnnouncementsPage(context, this, true));
        pages.put("mods", new ModsPage(context, this));
        pages.put("resourcepacks", new dev.yml.ui.page.ResourcePacksPage(context, this));
        pages.put("shader-configs", new dev.yml.ui.page.ShaderConfigsPage(context, this));
        pages.put("saves", new dev.yml.ui.page.SavesPage(context, this));
        pages.put("servers", new dev.yml.ui.page.ServersPage(context, this));
        pages.put("multiplayer", new dev.yml.ui.page.MultiplayerPage(context, this));
        pages.put("accounts", new AccountsPage(context, this));
        pages.put("admin-release", new dev.yml.ui.page.AdminReleasePage(context, this));
        pages.put("admin-announcements", new dev.yml.ui.page.AdminAnnouncementsPage(context, this));
        pages.put("settings", new SettingsPage(context, this));
        showPage("home");

        I18n.setLanguage(context.settings.language);
        I18n.localize(rootStack);
        sidebar.refreshLanguage();
        ensureInstanceDirs();

        sidebar.updateVersions(context.versions().size());
        context.io.submit(() -> {
            int javaCount = context.javaManager.refresh().size();
            Platform.runLater(() -> sidebar.updateJava(javaCount));
        });
    }

    public void show() {
        stage.show();
        if (context.settings.maximized) {
            stage.setMaximized(true);
        }
    }

    private HBox statusBar() {
        statusProgress.setPrefWidth(150);
        statusProgress.setMaxWidth(150);
        statusProgress.setVisible(false);
        statusProgress.setManaged(false);
        javafx.scene.control.Button logs = new javafx.scene.control.Button("日志");
        logs.getStyleClass().addAll("btn", "btn-small");
        logs.setOnAction(event -> openLog());
        HBox bar = new HBox(12, statusLabel, statusProgress, Ui.spacer(), logs);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 12, 4, 14));
        bar.setPrefHeight(30);
        bar.setMinHeight(30);
        return bar;
    }

    // --------------------------------------------------------------- pages

    public void showLanguageMenu() {
        sidebar.showLanguageMenu();
    }

    @Override
    public void showPage(String id) {
        Page page = pages.get(id);
        if (page == null) {
            return;
        }
        // Coalesce rapid clicks: run at most one switch per animation instead of stacking.
        if (switching) {
            pendingPage = id;
            return;
        }
        Page previous = pages.get(currentPage);
        if (previous != null && previous != page) {
            previous.onHide();
        }
        int oldIndex = pageOrder.indexOf(currentPage);
        int newIndex = pageOrder.indexOf(id);
        double direction = (oldIndex >= 0 && newIndex >= 0 && newIndex < oldIndex) ? -1 : 1;
        currentPage = id;
        sidebar.setActive(id);
        sidebar.setAnnouncementActive("announcements".equals(id) || "announcement-favorites".equals(id));
        javafx.scene.Node outgoing = content.getChildren().isEmpty()
                ? null : content.getChildren().get(0);
        javafx.scene.Node incoming = page.view();
        if (incoming != outgoing && incoming.getParent() instanceof javafx.scene.layout.Pane parentPane) {
            parentPane.getChildren().remove(incoming);
        }
        page.onShow();
        I18n.localize(incoming);
        if (incoming == outgoing) {
            // "versions" is an alias of the home page: refresh in place instead of
            // hiding and re-showing the exact same node (which looked like a flicker).
            incoming.setOpacity(1);
            incoming.setTranslateX(0);
            String next = pendingPage;
            pendingPage = null;
            if (next != null && !next.equals(currentPage)) {
                showPage(next);
            }
            return;
        }
        if (incoming != outgoing) {
            content.getChildren().add(incoming);
        }
        incoming.setOpacity(0);
        incoming.setTranslateX(18 * direction);
        javafx.animation.ParallelTransition animation = new javafx.animation.ParallelTransition();
        javafx.animation.Interpolator easing = javafx.animation.Interpolator.EASE_BOTH;
        if (outgoing != null && outgoing != incoming) {
            javafx.animation.FadeTransition outFade = new javafx.animation.FadeTransition(
                    javafx.util.Duration.millis(200), outgoing);
            outFade.setFromValue(1);
            outFade.setToValue(0);
            outFade.setInterpolator(easing);
            javafx.animation.TranslateTransition outSlide = new javafx.animation.TranslateTransition(
                    javafx.util.Duration.millis(220), outgoing);
            outSlide.setFromX(0);
            outSlide.setToX(-22 * direction);
            outSlide.setInterpolator(easing);
            animation.getChildren().addAll(outFade, outSlide);
        }
        javafx.animation.FadeTransition inFade = new javafx.animation.FadeTransition(
                javafx.util.Duration.millis(240), incoming);
        inFade.setFromValue(0);
        inFade.setToValue(1);
        inFade.setInterpolator(easing);
        javafx.animation.TranslateTransition inSlide = new javafx.animation.TranslateTransition(
                javafx.util.Duration.millis(260), incoming);
        inSlide.setFromX(18 * direction);
        inSlide.setToX(0);
        inSlide.setInterpolator(easing);
        animation.getChildren().addAll(inFade, inSlide);
        switching = true;
        animation.setOnFinished(event -> {
            if (outgoing != null && outgoing != incoming) {
                content.getChildren().remove(outgoing);
                outgoing.setOpacity(1);
                outgoing.setTranslateX(0);
            }
            incoming.setOpacity(1);
            incoming.setTranslateX(0);
            switching = false;
            String next = pendingPage;
            pendingPage = null;
            if (next != null && !next.equals(currentPage)) {
                showPage(next);
            }
        });
        animation.play();
    }

    @Override
    public void applyLanguage() {
        I18n.setLanguage(context.settings.language);
        I18n.localize(rootStack);
        Page page = pages.get(currentPage);
        if (page != null) {
            I18n.localize(page.view());
        }
        sidebar.refreshLanguage();
    }

    @Override
    public void navigate(String page) {
        showPage(page);
    }

    @Override
    public void refreshBackground() {
        background.apply();
        if (stage.getScene() != null) {
            ThemeManager.apply(stage.getScene(), context.settings);
        }
    }

    @Override
    public boolean importBackgroundImage(Path file) {
        return background.setLocalImage(file);
    }

    @Override
    public Path syncWallpaper() throws java.io.IOException {
        return background.syncDesktopWallpaper();
    }

    @Override
    public void refreshAll() {
        context.refreshVersions();
        ensureInstanceDirs();
        sidebar.updateVersions(context.versions().size());
        sidebar.refreshAccount();
        if (pages.get("versions") instanceof VersionsPage versions) {
            versions.refresh();
        }
        if (pages.get("home") instanceof HomePage home) {
            home.refresh();
        }
    }

    @Override
    public void toast(String message) {
        setStatus(message, false);
        dev.yml.ui.component.Toast.show(rootStack, message);
    }

    @Override
    public void openLog() {
        LogWindow window = LogWindow.get(stage);
        window.show();
    }

    @Override
    public void runTask(String title, java.util.function.Consumer<Progress> task, Runnable onDone) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        overlay.show(title, "准备中...", () -> cancelled.set(true));
        setStatus(title, true);
        context.io.submit(() -> {
            try {
                Progress progress = progressFor(cancelled);
                task.accept(progress);
                Platform.runLater(() -> {
                    overlay.hide();
                    setStatus(title + " 完成", false);
                    if (onDone != null) {
                        onDone.run();
                    }
                });
            } catch (TaskCancelledException e) {
                Platform.runLater(() -> {
                    overlay.hide();
                    setStatus("任务已取消", false);
                });
            } catch (Exception e) {
                Log.error(e, "任务失败: %s", title);
                Platform.runLater(() -> {
                    overlay.hide();
                    setStatus(title + " 失败", false);
                    Util.error(stage, "任务失败", rootMessage(e));
                });
            }
        });
    }

    @Override
    public javafx.stage.Window window() {
        return stage;
    }

    public void setStatus(String text, boolean busy) {        statusLabel.setText(text);
        statusProgress.setVisible(busy);
        statusProgress.setManaged(busy);
        if (!busy) {
            statusProgress.setProgress(0);
        }
    }

    private void ensureInstanceDirs() {
        String lang = context.settings.syncLanguageToGame ? I18n.gameCode() : null;
        for (LocalVersions.Installed version : context.versions()) {
            dev.yml.core.config.InstanceSettings settings = context.instanceSettings(version);
            Path dir = settings.isolated ? version.dir() : context.settings.mcPath();
            dev.yml.core.game.InstanceScaffolder.ensure(dir, version.id(),
                    version.loader().name(), settings.isolated, lang);
        }
    }

    private Progress progressFor(AtomicBoolean cancelled) {
        return new Progress() {
            @Override
            public void update(long done, long total, String note) {
                if (cancelled.get()) {
                    throw new TaskCancelledException();
                }
                overlay.update(total > 0 ? done / (double) total : -1, note);
            }

            @Override
            public void speed(long bytesPerSecond) {
                overlay.updateSpeed(bytesPerSecond);
            }

            @Override
            public boolean cancelled() {
                return cancelled.get();
            }
        };
    }

    // ------------------------------------------------------- self update

    public void showUpdateToast(dev.yml.core.update.UpdateService.Info info, Runnable onDownload,
                                Runnable onSkip) {
        hideUpdateToast();
        updateToast = new dev.yml.ui.component.UpdateToast(info, onDownload, onSkip, this::hideUpdateToast);
        StackPane.setAlignment(updateToast, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(updateToast, new Insets(0, 20, 52, 0));
        rootStack.getChildren().add(Math.max(0, rootStack.getChildren().size() - 1), updateToast);
    }

    public void hideUpdateToast() {
        if (updateToast != null) {
            rootStack.getChildren().remove(updateToast);
            updateToast = null;
        }
    }

    public void promptApplyUpdate(dev.yml.core.update.UpdateService.Info info) {
        boolean ok = Util.confirm(stage, "更新已下载",
                "启动器将关闭，2 秒后自动替换为新版本 " + info.version() + " 并重新启动。\n现在继续吗？");
        if (!ok) {
            return;
        }
        try {
            Path install = Path.of(MainWindow.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getParent();
            Path zip = context.dataRoot.resolve("updates").resolve("yml-" + info.version() + ".zip");
            Path script = dev.yml.core.update.UpdateService.writeUpdater(zip, install, context.dataRoot);
            dev.yml.core.update.UpdateService.launchUpdater(script);
            Platform.exit();
        } catch (Exception e) {
            Util.error(stage, "启动更新失败", e.getMessage());
        }
    }

    // -------------------------------------------------------------- launch

    @Override
    public void testLaunch(LocalVersions.Installed installed) {
        if (running != null && running.isAlive()) {
            Util.info(stage, "游戏正在运行", "请先退出当前游戏再测试。");
            return;
        }
        launch(installed);
        context.io.submit(() -> {
            try {
                Thread.sleep(20_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Process process = running;
            if (process != null && process.isAlive()) {
                Platform.runLater(() -> {
                    Util.info(stage, "测试启动成功",
                            "游戏已经正常启动并保持运行 20 秒，测试结束将关闭游戏。");
                    process.destroy();
                });
            } else {
                Platform.runLater(() -> Util.error(stage, "测试启动失败",
                        "游戏进程在 20 秒内退出，请查看日志或崩溃诊断。"));
            }
        });
    }

    @Override
    public void launch(LocalVersions.Installed installed) {
        if (installed == null || installed.broken()) {
            Util.error(stage, "无法启动", installed == null ? "未选择版本" : installed.error());
            return;
        }
        if (running != null && running.isAlive()) {
            Util.info(stage, "游戏正在运行", "当前已有游戏进程在运行，请先退出游戏。");
            return;
        }
        VersionInfo info = installed.info();
        InstanceSettings instance = context.instanceSettings(installed);
        JavaRuntime java = resolveJava(instance, info);
        if (java == null) {
            boolean ok = Util.confirm(stage, "缺少 Java 运行时",
                    "没有找到可用的 Java " + (info == null ? "" : info.javaMajor) + "。\n是否前往下载页安装？");
            if (ok) {
                showPage("download");
            }
            return;
        }
        int requiredJava = info == null ? 8 : Math.max(8, info.javaMajor);
        if (!context.settings.noJavaCompatCheck && java.major < requiredJava) {
            boolean go = Util.confirm(stage, "Java 版本可能不兼容",
                    "该版本需要 Java " + requiredJava + "，当前选择的是 Java " + java.major + "。\n"
                            + "继续启动可能出现崩溃或模组加载失败，仍要继续吗？");
            if (!go) {
                return;
            }
        }

        Account account = context.account();
        LaunchOptions options = LaunchOptions.of(context.settings.mcPath(), installed.id());
        options.javaExecutable = java.executable;
        options.memoryMb = dev.yml.core.game.MemoryRecommendation.effective(installed, instance);
        options.minMemoryMb = instance.minMemoryMb > 0 ? instance.minMemoryMb
                : Math.min(512, options.memoryMb / 4);
        options.addDefaultJvmArgs = !context.settings.noDefaultJvmArgs;
        options.addOptimizedJvmArgs = !context.settings.noJvmTuning;
        options.nativeLibraryPath = context.settings.nativeLibraryPath == null
                ? "" : context.settings.nativeLibraryPath.trim();
        if (context.settings.globalJvmArgs != null && !context.settings.globalJvmArgs.isBlank()) {
            for (String token : context.settings.globalJvmArgs.trim().split("\\s+")) {
                if (!token.isBlank()) {
                    options.globalJvmArgs.add(token);
                }
            }
        }
        if (!context.settings.noJavaCompatCheck) {
            java.util.List<String> warnings = new java.util.ArrayList<>();
            boolean bits32 = java.arch != null && (java.arch.contains("86") || java.arch.contains("32"));
            if (bits32 && options.memoryMb > 1024) {
                warnings.add("当前选择的是 32 位 Java，分配 " + options.memoryMb + " MB 堆内存通常无法启动。");
            }
            long totalMb = dev.yml.core.util.OsInfo.totalMemoryMb();
            if (totalMb > 0 && options.memoryMb > totalMb * 0.8) {
                warnings.add("分配内存 " + options.memoryMb + " MB 已超过物理内存的 80%（共 " + totalMb + " MB）。");
            }
            if (!warnings.isEmpty()) {
                boolean go = Util.confirm(stage, "启动参数提醒",
                        String.join("\n", warnings) + "\n仍要继续启动吗？");
                if (!go) {
                    return;
                }
            }
        }
        options.width = instance.width;
        options.height = instance.height;
        options.fullscreen = instance.fullscreen;
        options.isolated = instance.isolated;
        ensureInstanceDirs();
        if (context.settings.syncLanguageToGame) {
            dev.yml.core.game.GameLanguage.apply(options.gameDir(),
                    dev.yml.core.i18n.Language.gameCode(context.settings.language));
        }
        options.username = account.name;
        options.uuid = account.uuid == null || account.uuid.isBlank() ? Account.offlineUuid(account.name) : account.uuid;
        options.accessToken = account.accessToken;
        options.source = context.source();
        options.threads = context.settings.threads;
        options.extraJvmArgs.addAll(instance.jvmArgs);
        options.extraGameArgs.addAll(instance.gameArgs);
        options.userType = switch (account.type) {
            case MICROSOFT -> "msa";
            case EXTERNAL -> "mojang";
            default -> "legacy";
        };

        AtomicBoolean cancelled = new AtomicBoolean(false);
        overlay.show("正在启动 " + installed.id(), "准备游戏文件...", () -> cancelled.set(true));
        setStatus("正在启动 " + installed.id(), true);

        context.io.submit(() -> {
            try {
                Progress progress = progressFor(cancelled);
                if (account.type == Account.Type.EXTERNAL) {
                    try {
                        overlay.update(-1, "准备外置登录组件...");
                        Path injector = AuthlibInjector.ensure(context.dataRoot, progress);
                        options.extraJvmArgs.add(0, AuthlibInjector.javaAgentArgument(injector, account.apiRoot));
                    } catch (Exception e) {
                        throw new RuntimeException("无法准备外置登录组件: " + e.getMessage(), e);
                    }
                }
                if (account.type == Account.Type.MICROSOFT && account.expiresAt > 0
                        && account.expiresAt < System.currentTimeMillis() + 60_000
                        && account.refreshToken != null && !account.refreshToken.isBlank()) {
                    overlay.update(-1, "刷新微软登录令牌...");
                    try {
                        MicrosoftAuth.MinecraftAccount refreshed = MicrosoftAuth.refresh(
                                context.settings.microsoftClientId, account.refreshToken);
                        account.accessToken = refreshed.accessToken();
                        account.refreshToken = refreshed.refreshToken();
                        account.expiresAt = refreshed.expiresAt();
                        account.uuid = refreshed.uuid();
                        account.name = refreshed.name();
                        options.username = account.name;
                        options.uuid = account.uuid;
                        options.accessToken = account.accessToken;
                        context.settings.save();
                    } catch (Exception e) {
                        Log.warn("微软令牌刷新失败: %s", e.getMessage());
                    }
                }
                GameLauncher.Prepared prepared = GameLauncher.prepare(options, progress);
                if (cancelled.get()) {
                    throw new TaskCancelledException();
                }
                overlay.update(-1, "启动游戏进程...");
                Process process = GameLauncher.launch(prepared, line -> {
                    if (logWindow != null) {
                        logWindow.append(line);
                    }
                });
                running = process;
                Platform.runLater(() -> {
                    overlay.hide();
                    setStatus("游戏运行中 · " + installed.id(), false);
                    LogWindow window = LogWindow.get(stage);
                    window.setLogFile(prepared.logFile());
                    window.follow(prepared.gameDir().resolve("logs").resolve("latest.log"));
                    window.setAnalyzeAction(() -> openDiagnostics(installed));
                    logWindow = window;
                    if (context.settings.hideAfterLaunch) {
                        stage.hide();
                    }
                });
                int exit = process.waitFor();
                Platform.runLater(() -> {
                    setStatus("游戏已退出（代码 " + exit + "）", false);
                    if (context.settings.hideAfterLaunch && !stage.isShowing()) {
                        stage.show();
                    }
                });
                if (exit != 0 && context.settings.autoAnalyzeCrash) {
                    Path analyzeLog = newestGameLog(gameDir(installed));
                    analyzeAsync(installed, analyzeLog == null ? prepared.logFile() : analyzeLog);
                }
            } catch (TaskCancelledException e) {
                Platform.runLater(() -> {
                    overlay.hide();
                    setStatus("已取消启动", false);
                });
            } catch (Exception e) {
                Log.error(e, "启动失败");
                Platform.runLater(() -> {
                    overlay.hide();
                    setStatus("启动失败", false);
                    Util.error(stage, "启动失败", rootMessage(e));
                });
            } finally {
                running = null;
            }
        });
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        StringBuilder sb = new StringBuilder();
        while (cause != null) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
            cause = cause.getCause();
        }
        return sb.toString();
    }

    private JavaRuntime resolveJava(InstanceSettings instance, VersionInfo info) {
        if (instance.javaPath != null && !instance.javaPath.isBlank()) {
            JavaRuntime explicit = JavaRuntime.probe(Path.of(instance.javaPath), "实例设置");
            if (explicit != null) {
                return explicit;
            }
        }
        if (context.settings.defaultJavaPath != null && !context.settings.defaultJavaPath.isBlank()) {
            JavaRuntime explicit = JavaRuntime.probe(Path.of(context.settings.defaultJavaPath), "全局设置");
            if (explicit != null) {
                return explicit;
            }
        }
        int required = info == null ? 8 : Math.max(8, info.javaMajor);
        return context.javaManager.best(required);
    }

    @Override
    public void openInstanceSettings(LocalVersions.Installed version) {
        InstanceSettingsDialog dialog = new InstanceSettingsDialog(stage, context, version, () -> {
            refreshAll();
            toast("设置已保存");
        });
        dialog.show();
    }

    @Override
    public void openKeybinds(LocalVersions.Installed version) {
        if (version != null) {
            context.select(version);
        }
        showPage("keybinds");
    }

    @Override
    public void openMods(LocalVersions.Installed version) {
        if (version != null) {
            context.select(version);
        }
        showPage("mods");
    }

    @Override
    public void openResourcePacks(LocalVersions.Installed version) {
        if (version != null) {
            context.select(version);
        }
        showPage("resourcepacks");
    }

    @Override
    public void checkModUpdates(LocalVersions.Installed version) {
        if (version != null) {
            context.select(version);
        }
        showPage("mods");
        if (pages.get("mods") instanceof ModsPage modsPage) {
            modsPage.checkUpdatesNow();
        }
    }

    // ------------------------------------------------------------ diagnose

    @Override
    public void openDiagnostics(LocalVersions.Installed version) {
        if (version == null) {
            Util.info(stage, "没有选择版本", "请先在主页选择一个游戏版本。");
            return;
        }
        Path log = newestGameLog(gameDir(version));
        if (log == null) {
            Util.info(stage, "没有找到日志", "该版本还没有启动过，或日志目录不存在。");
            return;
        }
        analyzeAsync(version, log);
    }

    private Path gameDir(LocalVersions.Installed version) {
        InstanceSettings instance = context.instanceSettings(version);
        if (instance.gameDirOverride != null && !instance.gameDirOverride.isBlank()) {
            return Path.of(instance.gameDirOverride);
        }
        return instance.isolated ? version.dir() : context.settings.mcPath();
    }

    private static Path newestGameLog(Path gameDir) {
        Path dir = gameDir.resolve("logs");
        if (!Files.isDirectory(dir)) {
            return null;
        }
        Path latest = dir.resolve("latest.log");
        if (Files.isRegularFile(latest)) {
            return latest;
        }
        Path best = null;
        long bestTime = 0;
        try (var stream = Files.list(dir)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".log")) {
                    continue;
                }
                if (!name.startsWith("yml-") && !name.equals("latest.log")) {
                    continue;
                }
                long time = file.toFile().lastModified();
                if (time > bestTime) {
                    bestTime = time;
                    best = file;
                }
            }
        } catch (Exception ignored) {
        }
        return best;
    }

    private void analyzeAsync(LocalVersions.Installed version, Path logFile) {
        if (logFile == null) {
            return;
        }
        Path gameDir = gameDir(version);
        setStatus("正在分析日志...", true);
        context.io.submit(() -> {
            try {
                Path crash = LogAnalyzer.latestCrashReport(gameDir);
                LogAnalyzer.Report report = LogAnalyzer.analyzeFiles(logFile, crash);
                Path exported = null;
                if (!report.confident()) {
                    exported = LogExporter.export(gameDir, logFile, crash, report);
                }
                Path finalExported = exported;
                Platform.runLater(() -> {
                    setStatus("日志分析完成", false);
                    new CrashAnalysisDialog(stage, version.id(), gameDir, logFile, crash, report, finalExported)
                            .show();
                });
            } catch (Exception e) {
                Log.error(e, "日志分析失败");
                Platform.runLater(() -> {
                    setStatus("日志分析失败", false);
                    Util.error(stage, "日志分析失败", rootMessage(e));
                });
            }
        });
    }

    // -------------------------------------------------------------- resize

    private void installWindowResize(Scene scene) {
        final double border = 6;
        final double[] state = new double[7]; // mode, startX, startY, startW, startH, startScreenX, startScreenY

        scene.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (stage.isMaximized()) {
                scene.setCursor(Cursor.DEFAULT);
                return;
            }
            scene.setCursor(cursorFor(event.getSceneX(), event.getSceneY(), scene.getWidth(), scene.getHeight(), border));
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (stage.isMaximized() || event.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                return;
            }
            int mode = modeFor(event.getSceneX(), event.getSceneY(), scene.getWidth(), scene.getHeight(), border);
            if (mode != 0) {
                state[0] = mode;
                state[1] = stage.getX();
                state[2] = stage.getY();
                state[3] = stage.getWidth();
                state[4] = stage.getHeight();
                state[5] = event.getScreenX();
                state[6] = event.getScreenY();
                event.consume();
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (state[0] == 0) {
                return;
            }
            int mode = (int) state[0];
            double dx = event.getScreenX() - state[5];
            double dy = event.getScreenY() - state[6];
            double x = state[1];
            double y = state[2];
            double w = state[3];
            double h = state[4];
            double minW = stage.getMinWidth();
            double minH = stage.getMinHeight();
            if ((mode & 1) != 0) { // left
                double newW = Math.max(minW, w - dx);
                x = x + (w - newW);
                w = newW;
            }
            if ((mode & 2) != 0) { // right
                w = Math.max(minW, w + dx);
            }
            if ((mode & 4) != 0) { // top
                double newH = Math.max(minH, h - dy);
                y = y + (h - newH);
                h = newH;
            }
            if ((mode & 8) != 0) { // bottom
                h = Math.max(minH, h + dy);
            }
            stage.setX(x);
            stage.setY(y);
            stage.setWidth(w);
            stage.setHeight(h);
            event.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> state[0] = 0);
    }

    private static int modeFor(double x, double y, double w, double h, double border) {
        int mode = 0;
        if (x <= border) {
            mode |= 1;
        } else if (x >= w - border) {
            mode |= 2;
        }
        if (y <= border) {
            mode |= 4;
        } else if (y >= h - border) {
            mode |= 8;
        }
        return mode;
    }

    private static Cursor cursorFor(double x, double y, double w, double h, double border) {
        int mode = modeFor(x, y, w, h, border);
        return switch (mode) {
            case 1, 2 -> Cursor.H_RESIZE;
            case 4, 8 -> Cursor.V_RESIZE;
            case 1 | 4, 2 | 8 -> Cursor.NW_RESIZE;
            case 2 | 4, 1 | 8 -> Cursor.NE_RESIZE;
            default -> Cursor.DEFAULT;
        };
    }

    public AppContext context() {
        return context;
    }

    public Page page(String id) {
        return pages.get(id);
    }

    public Stage stage() {
        return stage;
    }
}
