package dev.yml.ui.page;

import dev.yml.core.json.Json;
import dev.yml.core.net.Downloader;
import dev.yml.core.net.Http;
import dev.yml.ui.AppContext;
import dev.yml.ui.Page;
import dev.yml.ui.Ui;
import dev.yml.ui.UiHost;
import dev.yml.ui.Util;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Download/install entry points for multiplayer tools (Terracotta / SakuraFrp). */
public final class MultiplayerPage implements Page {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane view = new BorderPane();
    private final Label status = Ui.label("", "tiny", "faint");

    public MultiplayerPage(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        view.setPadding(new Insets(18, 20, 14, 20));
        HBox header = Ui.row(10, Ui.h1("联机"),
                Ui.small("启动器不内置联机本体，提供下载与安装入口"));
        header.setAlignment(Pos.CENTER_LEFT);
        VBox cards = new VBox(14, terracottaCard(), sakuraCard());
        view.setTop(new VBox(10, header));
        view.setCenter(cards);
        view.setBottom(status);
        BorderPane.setMargin(status, new Insets(8, 0, 0, 2));
    }

    private Node terracottaCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 16, 18));
        Label title = Ui.h3("陶瓦联机（Terracotta）");
        Label desc = Ui.label("集成官方 Windows 客户端：房主创建房间、房客输入邀请码，界面与陶瓦客户端一致。"
                + "启动器负责下载、解压和启动。", "small", "muted");
        desc.setWrapText(true);
        Button download = new Button("下载 / 更新客户端");
        download.getStyleClass().addAll("btn", "btn-primary", "btn-small");
        download.setOnAction(event -> downloadTerracotta());
        Button launch = new Button("启动陶瓦联机");
        launch.getStyleClass().addAll("btn", "btn-small");
        launch.setOnAction(event -> launchTerracotta());
        Button project = new Button("项目页");
        project.getStyleClass().addAll("btn", "btn-small");
        project.setOnAction(event -> Util.openUrl("https://github.com/burningtnt/Terracotta"));
        Button mods = new Button("当前实例 mods");
        mods.getStyleClass().addAll("btn", "btn-small");
        mods.setOnAction(event -> {
            if (context.selected() != null) {
                Util.openFolder(context.selected().modsDir(context.settings.mcPath(),
                        context.instanceSettings(context.selected()).isolated));
            }
        });
        card.getChildren().addAll(title, desc, new HBox(8, download, launch, project, mods));
        return card;
    }

    private Path terracottaExe() {
        Path root = context.dataRoot.resolve("tools").resolve("Terracotta");
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (var stream = Files.walk(root, 4)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                            .endsWith(".exe"))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void launchTerracotta() {
        Path exe = terracottaExe();
        if (exe == null) {
            host.toast("还没有下载陶瓦联机客户端");
            downloadTerracotta();
            return;
        }
        try {
            new ProcessBuilder(exe.toString())
                    .directory(exe.getParent().toFile())
                    .start();
        } catch (Exception e) {
            Util.error(host.window(), "启动失败", e.getMessage());
        }
    }

    private void downloadTerracotta() {
        host.runTask("下载陶瓦联机客户端", progress -> {
            try {
                progress.note("查询最新版本...");
                String json = Http.text(
                        "https://api.github.com/repos/burningtnt/Terracotta/releases/latest",
                        2 * 1024 * 1024);
                Map<String, Object> root = Json.obj(Json.parse(json));
                String url = null;
                String name = null;
                for (Object item : Json.arrOrEmpty(root.get("assets"))) {
                    Map<String, Object> asset = Json.objOrEmpty(item);
                    String assetName = Json.str(asset.get("name"), "");
                    String lower = assetName.toLowerCase(java.util.Locale.ROOT);
                    if (lower.contains("windows") && lower.contains("x86_64")
                            && lower.endsWith(".tar.gz")) {
                        url = Json.str(asset.get("browser_download_url"));
                        name = assetName;
                        break;
                    }
                }
                if (url == null) {
                    throw new IllegalStateException("没有找到 Windows 客户端资源");
                }
                Path dir = context.dataRoot.resolve("tools").resolve("Terracotta");
                Files.createDirectories(dir);
                Path archive = dir.resolve(name);
                progress.note("下载 " + name);
                new Downloader(1).downloadAll(
                        List.of(Downloader.Item.of(name, url, archive, null, 0)), progress);
                Path extract = dir.resolve("client");
                Files.createDirectories(extract);
                progress.note("解压客户端...");
                Process tar = new ProcessBuilder("tar", "-xzf", archive.toString(),
                        "-C", extract.toString()).inheritIO().start();
                tar.waitFor();
                Path exe = terracottaExe();
                if (exe == null) {
                    throw new IllegalStateException("解压后没有找到客户端 exe");
                }
                Util.openFolder(exe.getParent());
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }, () -> host.toast("陶瓦联机客户端已就绪，可点击启动"));
    }

    private Node sakuraCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 16, 18));
        Label title = Ui.h3("樱花 FRP（SakuraFrp）");
        Label desc = Ui.label("内网穿透工具，用于把本地服务器暴露到公网。"
                + "启动器会从官方 GitHub Release 下载 Windows 客户端到工具目录。", "small", "muted");
        desc.setWrapText(true);
        Button download = new Button("下载并安装 Windows 客户端");
        download.getStyleClass().addAll("btn", "btn-primary", "btn-small");
        download.setOnAction(event -> downloadSakura());
        Button official = new Button("打开官网");
        official.getStyleClass().addAll("btn", "btn-small");
        official.setOnAction(event -> Util.openUrl("https://www.natfrp.com/"));
        Button tools = new Button("打开工具目录");
        tools.getStyleClass().addAll("btn", "btn-small");
        tools.setOnAction(event -> Util.openFolder(context.dataRoot.resolve("tools")));
        card.getChildren().addAll(title, desc, new HBox(8, download, official, tools));
        return card;
    }

    private void downloadSakura() {
        host.runTask("下载 SakuraFrp 客户端", progress -> {
            try {
                progress.note("准备下载客户端...");
                String url = "https://nyat-static.globalslb.net/natfrp/client/launcher-windows/3.2.0.2/SakuraLauncher.exe";
                String name = "SakuraLauncher.exe";
                Path dir = context.dataRoot.resolve("tools").resolve("SakuraFrp");
                Files.createDirectories(dir);
                Path target = dir.resolve(name);
                progress.note("下载 " + name);
                new Downloader(1).downloadAll(List.of(Downloader.Item.of(name, url, target, null, 0)), progress);
                if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                    progress.note("解压...");
                    Path extract = dir.resolve("client");
                    Files.createDirectories(extract);
                    try (var zip = new java.util.zip.ZipFile(target.toFile())) {
                        var entries = zip.entries();
                        while (entries.hasMoreElements()) {
                            var entry = entries.nextElement();
                            if (entry.isDirectory()) {
                                continue;
                            }
                            Path dest = extract.resolve(entry.getName()).normalize();
                            if (!dest.startsWith(extract)) {
                                continue;
                            }
                            Files.createDirectories(dest.getParent());
                            try (var in = zip.getInputStream(entry)) {
                                Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                    Util.openFolder(extract);
                } else {
                    Util.openFolder(dir);
                }
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }, () -> {
            status.setText("SakuraFrp 已下载到 " + context.dataRoot.resolve("tools").resolve("SakuraFrp"));
            host.toast("SakuraFrp 客户端下载完成");
        });
    }

    @Override
    public Node view() {
        return view;
    }

    @Override
    public void onShow() {
        status.setText("工具目录：" + context.dataRoot.resolve("tools"));
    }
}
