package dev.yml;

import dev.yml.core.AppPaths;
import dev.yml.core.config.Settings;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;
import dev.yml.ui.AppContext;
import dev.yml.ui.DebugShot;
import dev.yml.ui.MainWindow;
import dev.yml.ui.page.DownloadPage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.nio.file.Path;

/** Desktop entry point. */
public final class Main extends Application {

    private String screenshot;
    private String startPage;
    private String downloadTab;
    private String contentType;
    private String toastText;
    private String dialog;
    private String feedUrl;
    private boolean adminMode;
    private boolean adminAuthDemo;
    private boolean langMenu;
    private boolean flatDialog;
    private long shotDelay = 1800;

    @Override
    public void start(Stage stage) {
        for (String arg : getParameters().getRaw()) {
            if (arg.startsWith("--screenshot=")) {
                screenshot = arg.substring("--screenshot=".length());
            } else if (arg.startsWith("--page=")) {
                startPage = arg.substring("--page=".length());
            } else if (arg.startsWith("--download-tab=")) {
                downloadTab = arg.substring("--download-tab=".length());
            } else if (arg.startsWith("--toast=")) {
                toastText = arg.substring("--toast=".length());
            } else if (arg.startsWith("--content-type=")) {
                contentType = arg.substring("--content-type=".length());
            } else if (arg.startsWith("--dialog=")) {
                dialog = arg.substring("--dialog=".length());
            } else if (arg.startsWith("--feed=")) {
                feedUrl = arg.substring("--feed=".length());
            } else if (arg.startsWith("--launcher-dir=")) {
                System.setProperty("yml.launcher.dir", arg.substring("--launcher-dir=".length()));
            } else if (arg.equals("--portable")) {
                System.setProperty("yml.portable", "true");
            } else if (arg.equals("--admin")) {
                adminMode = true;
            } else if (arg.equals("--admin-auth")) {
                adminAuthDemo = true;
            } else if (arg.equals("--lang-menu")) {
                langMenu = true;
            } else if (arg.equals("--flat-dialog")) {
                flatDialog = true;
            } else if (arg.startsWith("--shot-delay=")) {
                shotDelay = Long.parseLong(arg.substring("--shot-delay=".length()));
            }
        }

        Path dataRoot = AppPaths.defaultDataRoot();
        FileUtil.ensureDir(dataRoot);
        AppPaths.migrateLegacy(dataRoot);
        Log.init(dataRoot.resolve("logs").resolve("launcher.log"), false);
        Log.info("Yumoo Minecraft Launcher启动，数据目录 %s", dataRoot);

        Settings settings = Settings.load(dataRoot);
        if (Boolean.getBoolean("yml.portable")
                && !java.nio.file.Files.exists(AppPaths.pathsFile())) {
            settings.mcRoot = AppPaths.portableMcRoot().toString();
        }
        java.nio.file.Path mcRoot = java.nio.file.Path.of(settings.mcRoot);
        FileUtil.ensureDir(dataRoot);
        FileUtil.ensureDir(mcRoot);
        dev.yml.core.game.InstanceScaffolder.ensure(mcRoot, "base", "VANILLA", false, null);
        if (!java.nio.file.Files.exists(AppPaths.pathsFile())) {
            AppPaths.writePointer(dataRoot, mcRoot);
            settings.save();
        }
        AppContext context = new AppContext(dataRoot, settings);
        context.refreshVersions();

        MainWindow window = new MainWindow(stage, context, adminMode);
        window.show();
        window.setStatus("已加载 " + context.versions().size() + " 个实例", false);
        if (toastText != null) {
            javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(
                    javafx.util.Duration.millis(700));
            delay.setOnFinished(event -> window.toast(toastText));
            delay.play();
        }
        window.setStatus("已加载 " + context.versions().size() + " 个实例", false);
        if (startPage != null) {
            window.showPage(startPage);
        }
        if (langMenu) {
            Platform.runLater(window::showLanguageMenu);
        }
        if (flatDialog) {
            Platform.runLater(() -> dev.yml.ui.Util.info(stage, "YML 提示",
                    "这是一个扁平化弹窗示例：无多余留白、直角边框、紧凑按钮。"));
        }
        if (adminAuthDemo) {
            Platform.runLater(() -> dev.yml.core.admin.AdminAuth.prompt(stage, dataRoot,
                    dev.yml.core.admin.AdminAuth.Action.ANNOUNCEMENT));
        }
        if ("instance".equals(dialog) && context.selected() != null) {
            Platform.runLater(() -> window.openInstanceSettings(context.selected()));
        }
        String feed = feedUrl != null && !feedUrl.isBlank() ? feedUrl : context.settings.updateFeedUrl;
        if ((feed == null || feed.isBlank()) && System.getProperty("yml.feed") != null) {
            feed = System.getProperty("yml.feed");
        }
        if (feed != null && !feed.isBlank()) {
            checkUpdates(window, context, feed);
        }
        if (downloadTab != null && pagesDownload(window) instanceof DownloadPage download) {
            download.select(downloadTab);
            if (contentType != null) {
                download.selectContentType(contentType);
            }
        }
        if (screenshot != null) {
            DebugShot.capture(stage, screenshot, shotDelay, Platform::exit);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static void checkUpdates(MainWindow window, AppContext context, String feed) {
        context.io.submit(() -> {
            try {
                dev.yml.core.update.UpdateService.Info info = dev.yml.core.update.UpdateService.fetch(feed);
                if (info == null || !dev.yml.core.update.UpdateService.isNewer(info.version())) {
                    return;
                }
                if (info.version().equals(context.settings.skippedUpdateVersion)) {
                    return;
                }
                Platform.runLater(() -> window.showUpdateToast(info,
                        () -> window.runTask("下载启动器更新", progress -> {
                            try {
                                Path zip = dev.yml.core.update.UpdateService.download(info,
                                        context.dataRoot.resolve("updates"), progress);
                                progress.note("更新包已下载: " + zip.getFileName());
                            } catch (java.io.IOException e) {
                                throw new RuntimeException(e.getMessage(), e);
                            }
                        }, () -> window.promptApplyUpdate(info)),
                        () -> {
                            context.settings.skippedUpdateVersion = info.version();
                            context.settings.save();
                            window.hideUpdateToast();
                        }));
            } catch (Exception e) {
                Log.debug("检查启动器更新失败: %s", e.getMessage());
            }
        });
    }

    private static Object pagesDownload(MainWindow window) {
        return window.page("download");
    }
}
