package dev.yml.ui.page;

import dev.yml.core.auth.Account;
import dev.yml.core.config.InstanceSettings;
import dev.yml.core.game.LaunchOptions;
import dev.yml.core.game.LocalVersions;
import dev.yml.core.game.LoaderType;
import dev.yml.core.game.VersionInfo;
import dev.yml.core.java.JavaRuntime;
import dev.yml.ui.AppContext;
import dev.yml.ui.Page;
import dev.yml.ui.Ui;
import dev.yml.ui.Util;
import dev.yml.ui.component.CubeIcon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Full version management: list, details, quick actions. */
public final class VersionsPage implements Page {

    public interface Host {
        void launch(LocalVersions.Installed version);

        void testLaunch(LocalVersions.Installed version);

        void openInstanceSettings(LocalVersions.Installed version);

        void openKeybinds(LocalVersions.Installed version);

        void openMods(LocalVersions.Installed version);

        void openResourcePacks(LocalVersions.Installed version);

        void checkModUpdates(LocalVersions.Installed version);

        void openDiagnostics(LocalVersions.Installed version);

        void navigate(String page);

        void toast(String message);

        void refreshAll();

        void runTask(String title, java.util.function.Consumer<dev.yml.core.util.Progress> task,
                     Runnable onDone);

        javafx.stage.Window window();
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());

    private final Host host;
    private final AppContext context;

    private final VBox versionList = new VBox(4);
    private final ScrollPane listScroll = new ScrollPane(versionList);
    private final TextField search = new TextField();
    private final StackPane detail = new StackPane();
    private final Label countLabel = Ui.label("0 个实例", "tiny", "faint");

    private String filter = "";

    public VersionsPage(Host host, AppContext context) {
        this.host = host;
        this.context = context;
        build();
    }

    private void build() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(18, 20, 16, 20));

        // -------------------------------------------------------------- list
        VBox left = new VBox(10);
        left.setPrefWidth(264);
        left.setMinWidth(232);
        left.setMaxWidth(300);

        HBox header = Ui.row(8, Ui.h2("游戏版本"), Ui.spacer(), countLabel);
        search.setPromptText("搜索版本 / 加载器");
        search.textProperty().addListener((obs, old, value) -> {
            filter = value == null ? "" : value.trim().toLowerCase();
            refreshList();
        });
        HBox searchBox = new HBox(6);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getChildren().add(search);
        HBox.setHgrow(search, Priority.ALWAYS);

        Button refresh = new Button();
        refresh.setGraphic(dev.yml.ui.component.Icon.of(dev.yml.ui.component.Icon.REFRESH, 14, Ui.TEXT_DIM));
        refresh.getStyleClass().addAll("btn", "btn-small");
        refresh.setOnAction(event -> {
            context.refreshVersions();
            refreshList();
            buildDetail();
        });
        Ui.tooltip(refresh, "重新扫描本地版本");
        searchBox.getChildren().add(refresh);

        listScroll.setFitToWidth(true);
        listScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(listScroll, Priority.ALWAYS);

        Button add = new Button("添加实例");
        add.setGraphic(dev.yml.ui.component.Icon.of(dev.yml.ui.component.Icon.PLUS, 13, Ui.TEXT));
        add.getStyleClass().addAll("btn", "btn-small");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(event -> host.navigate("download"));

        left.getChildren().addAll(header, searchBox, listScroll, add);

        // ------------------------------------------------------------ detail
        detail.setPadding(new Insets(0, 0, 0, 18));
        BorderPane.setMargin(left, new Insets(0, 0, 0, 0));
        root.setLeft(left);
        root.setCenter(detail);
        root.setBottom(bottomBar());
        view = root;
    }

    private Label bottomVersion = Ui.label("未选择版本", "small", "muted");
    private Button bottomLaunch;
    private Button bottomManage;

    private Node bottomBar() {
        bottomLaunch = new Button("启动游戏");
        bottomLaunch.getStyleClass().add("btn-launch");
        bottomLaunch.setGraphic(dev.yml.ui.component.Icon.of(dev.yml.ui.component.Icon.PLAY, 17,
                javafx.scene.paint.Color.WHITE, true));
        bottomLaunch.setOnAction(event -> {
            LocalVersions.Installed selected = context.selected();
            if (selected != null) {
                host.launch(selected);
            }
        });

        Button browse = new Button("浏览 / 切换版本");
        browse.getStyleClass().addAll("btn");
        browse.setGraphic(dev.yml.ui.component.Icon.of(dev.yml.ui.component.Icon.GRID, 14, Ui.TEXT));
        browse.setOnAction(event -> switchVersion());

        bottomManage = new Button("管理当前版本");
        bottomManage.getStyleClass().add("btn");
        bottomManage.setGraphic(dev.yml.ui.component.Icon.of(dev.yml.ui.component.Icon.SLIDERS, 14, Ui.TEXT));
        bottomManage.setOnAction(event -> {
            LocalVersions.Installed selected = context.selected();
            if (selected != null) {
                host.openInstanceSettings(selected);
            }
        });

        HBox actions = new HBox(8, bottomVersion, browse, bottomManage);
        actions.setAlignment(Pos.CENTER_LEFT);

        HBox bar = new HBox(12, actions, Ui.spacer(), bottomLaunch);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.getStyleClass().add("card");
        BorderPane.setMargin(bar, new Insets(12, 0, 0, 0));
        updateBottomBar();
        return bar;
    }

    private void updateBottomBar() {
        LocalVersions.Installed selected = context.selected();
        if (bottomVersion != null) {
            bottomVersion.setText(selected == null ? "未选择版本" : "当前版本 · " + selected.id());
        }
        if (bottomLaunch != null) {
            bottomLaunch.setDisable(selected == null || selected.broken());
        }
        if (bottomManage != null) {
            bottomManage.setDisable(selected == null);
        }
    }

    private void switchVersion() {
        javafx.stage.Window owner = detail.getScene() == null ? null : detail.getScene().getWindow();
        new dev.yml.ui.VersionSwitchDialog(owner, context, version -> {
            context.select(version);
            refreshList();
            buildDetail();
        }).show();
    }

    private Node view;

    @Override
    public Node view() {
        return view;
    }

    @Override
    public void onShow() {
        context.refreshVersions();
        refreshList();
        buildDetail();
    }

    @Override
    public void onHide() {
        if (skinPreview != null) {
            skinPreview.stop();
        }
    }

    public void refresh() {
        refreshList();
        buildDetail();
    }

    private void refreshList() {
        versionList.getChildren().clear();
        List<LocalVersions.Installed> versions = context.versions();
        List<LocalVersions.Installed> visible = new ArrayList<>();
        for (LocalVersions.Installed installed : versions) {
            String haystack = (installed.id() + " " + installed.loader().label() + " " + installed.baseVersion())
                    .toLowerCase();
            if (filter.isEmpty() || haystack.contains(filter)) {
                visible.add(installed);
            }
        }
        countLabel.setText(visible.size() + " 个实例");
        visible.sort((a, b) -> Boolean.compare(isFavorite(b), isFavorite(a)));
        if (visible.isEmpty()) {
            VBox empty = new VBox(8, Ui.label("没有找到版本", "h3"), Ui.small("去下载页安装一个版本吧"));
            empty.setPadding(new Insets(24, 12, 12, 12));
            empty.setAlignment(Pos.CENTER);
            versionList.getChildren().add(empty);
            return;
        }
        for (LocalVersions.Installed installed : visible) {
            versionList.getChildren().add(versionRow(installed));
        }
    }

    private Node versionRow(LocalVersions.Installed installed) {
        Node cubeBox = versionThumb(installed, 36);

        Label name = Ui.label(installed.id(), "h3");
        name.setMaxWidth(176);
        name.setMinWidth(0);
        name.setWrapText(true);
        name.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        HBox chips = new HBox(5);
        chips.setAlignment(Pos.CENTER_LEFT);
        chips.setMaxWidth(188);
        if (installed.broken()) {
            chips.getChildren().add(Ui.chip("无法读取", "chip-red"));
        } else {
            chips.getChildren().add(Ui.chip(installed.loader().label(), loaderChip(installed.loader())));
            chips.getChildren().add(Ui.chip(installed.baseVersion()));
        }
        VBox titles = new VBox(3, name, chips);
        titles.setAlignment(Pos.CENTER_LEFT);
        titles.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titles, Priority.ALWAYS);

        boolean favorite = isFavorite(installed);
        Button star = new Button(favorite ? "★" : "☆");
        star.setStyle("-fx-font-size: 17px; -fx-text-fill: #f2b84b; -fx-background-color: transparent;"
                + " -fx-border-color: transparent; -fx-padding: 0 4 0 4;");
        star.setCursor(javafx.scene.Cursor.HAND);
        Ui.tooltip(star, favorite ? "取消常玩收藏" : "设为常玩版本");
        star.setOnAction(event -> {
            event.consume();
            context.toggleFavorite(installed.id());
            refreshList();
            buildDetail();
        });

        HBox row = new HBox(10, cubeBox, titles, Ui.spacer(), star);
        row.getStyleClass().add("version-row");
        row.setAlignment(Pos.CENTER_LEFT);
        if (context.selected() != null && context.selected().id().equals(installed.id())) {
            row.getStyleClass().add("version-row-selected");
        }
        row.setOnMouseClicked(event -> {
            context.select(installed);
            refreshList();
            buildDetail();
        });
        return row;
    }

    private static String loaderChip(LoaderType loader) {
        return switch (loader) {
            case FABRIC -> "chip-orange";
            case FORGE -> "chip-accent";
            case NEOFORGE -> "chip-orange";
            case OPTIFINE -> "chip-accent";
            default -> "";
        };
    }

    // -------------------------------------------------------------- detail

    private void buildDetail() {
        detail.getChildren().clear();
        LocalVersions.Installed selected = context.selected();
        if (selected == null) {
            VBox empty = new VBox(14);
            empty.setAlignment(Pos.CENTER);
            empty.getChildren().addAll(
                    IconBig(),
                    Ui.h2("还没有选择版本"),
                    Ui.small("从左侧选择一个实例，或前往下载页安装"));
            Button download = new Button("前往下载");
            download.getStyleClass().addAll("btn", "btn-primary");
            download.setOnAction(event -> host.navigate("download"));
            empty.getChildren().add(download);
            detail.getChildren().add(empty);
            return;
        }
        VBox content = new VBox(14);
        content.setAlignment(Pos.TOP_LEFT);
        content.getChildren().add(banner(selected));
        content.getChildren().add(infoGrid(selected));
        content.getChildren().add(quickActions(selected));
        content.getChildren().add(skinCard());
        VBox.setVgrow(content, Priority.ALWAYS);
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent;");
        detail.getChildren().add(scroll);
        updateBottomBar();
    }

    private dev.yml.ui.component.SkinPreview3D skinPreview;
    private Label skinStatus;

    private Node skinCard() {
        if (skinPreview != null) {
            skinPreview.stop();
        }
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14, 16, 14, 16));

        dev.yml.core.auth.Account account = context.account();
        skinStatus = Ui.label("", "tiny", "faint");
        HBox header = Ui.row(8, Ui.h3("皮肤预览"), Ui.spacer(), skinStatus);
        header.setAlignment(Pos.CENTER_LEFT);

        skinPreview = new dev.yml.ui.component.SkinPreview3D(180, 200);
        skinPreview.setSkin(dev.yml.core.skin.SkinService.skin(account));

        Button steve = new Button("默认 Steve");
        Button alex = new Button("默认 Alex");
        Button premium = new Button("正版皮肤");
        Button local = new Button("本地 PNG");
        for (Button button : new Button[]{steve, alex, premium, local}) {
            button.getStyleClass().addAll("btn", "btn-small");
        }
        steve.setOnAction(event -> setSkinMode(account, "STEVE", null));
        alex.setOnAction(event -> setSkinMode(account, "ALEX", null));
        premium.setOnAction(event -> choosePremiumSkin(account));
        local.setOnAction(event -> chooseLocalSkin(account));

        VBox actions = new VBox(8, steve, alex, premium, local);
        actions.setPadding(new Insets(4, 0, 0, 8));
        HBox body = new HBox(10, skinPreview, actions);
        body.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(header, body);
        updateSkinStatus(account);
        return card;
    }

    private void updateSkinStatus(dev.yml.core.auth.Account account) {
        String mode = account.skinMode == null ? "STEVE" : account.skinMode;
        skinStatus.setText(switch (mode) {
            case "ALEX" -> "默认 Alex";
            case "PREMIUM" -> "正版皮肤";
            case "LOCAL" -> "本地皮肤";
            default -> "默认 Steve";
        });
    }

    private void setSkinMode(dev.yml.core.auth.Account account, String mode, String path) {
        account.skinMode = mode;
        account.skinPath = path;
        context.settings.save();
        if (skinPreview != null) {
            skinPreview.setSkin(dev.yml.core.skin.SkinService.skin(account));
        }
        updateSkinStatus(account);
        host.refreshAll();
    }

    private void chooseLocalSkin(dev.yml.core.auth.Account account) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("选择皮肤 PNG（64x64 或 64x32）");
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Minecraft 皮肤 (*.png)", "*.png"));
        java.io.File file = chooser.showOpenDialog(detail.getScene() == null ? null
                : detail.getScene().getWindow());
        if (file == null) {
            return;
        }
        if (!dev.yml.core.skin.SkinService.validSkin(file.toPath())) {
            Util.error(host.window(), "皮肤无效", "请选择 64x64 或 64x32 的 Minecraft 皮肤 PNG。");
            return;
        }
        try {
            java.nio.file.Path copy = dev.yml.core.skin.SkinService.localCopy(account, file.toPath());
            setSkinMode(account, "LOCAL", copy.toString());
        } catch (Exception e) {
            Util.error(host.window(), "读取皮肤失败", e.getMessage());
        }
    }

    private void choosePremiumSkin(dev.yml.core.auth.Account account) {
        if (account.name == null || account.name.isBlank()) {
            return;
        }
        skinStatus.setText("查询正版皮肤...");
        context.io.submit(() -> {
            boolean available = dev.yml.core.skin.SkinService.premiumAvailable(account.name);
            javafx.application.Platform.runLater(() -> {
                if (!available) {
                    updateSkinStatus(account);
                    Util.info(host.window(), "没有正版皮肤",
                            "名称 " + account.name + " 不是正版账户，或该账户没有自定义皮肤。");
                    return;
                }
                setSkinMode(account, "PREMIUM", null);
                Util.info(host.window(), "已切换正版皮肤",
                        "如果皮肤没有立即显示，稍后重新打开主页即可。");
            });
        });
    }

    private Node IconBig() {
        CubeIcon cube = new CubeIcon(LoaderType.VANILLA, 72);
        StackPane box = new StackPane(cube);
        box.setMinSize(80, 80);
        return box;
    }

    private Node banner(LocalVersions.Installed installed) {
        VersionInfo info = installed.info();
        VBox banner = new VBox(8);
        banner.getStyleClass().add("banner");
        banner.setPadding(new Insets(20, 22, 18, 22));

        HBox titleRow = new HBox(14);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Node iconBox = versionThumb(installed, 48);
        Label id = Ui.h1(installed.id());
        HBox chips = new HBox(6);
        chips.getChildren().add(Ui.chip(installed.loader().label(), loaderChip(installed.loader())));
        if (!installed.baseVersion().equals(installed.id())) {
            chips.getChildren().add(Ui.chip("基于 " + installed.baseVersion(), "chip-accent"));
        }
        if (info != null) {
            chips.getChildren().add(Ui.chip(typeLabel(info.type)));
        }
        VBox titleBox = new VBox(5, id, chips);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleRow.getChildren().addAll(iconBox, titleBox);

        String subtitle = info == null
                ? "该版本信息无法读取"
                : "需要 Java " + info.javaMajor + " · " + (info.releaseTime == null ? "未知发布日期"
                : "发布于 " + info.releaseTime.substring(0, 10));
        Label sub = Ui.label(subtitle, "small", "muted");

        banner.getChildren().addAll(titleRow, sub);
        return banner;
    }

    private static String typeLabel(String type) {
        return switch (type == null ? "release" : type) {
            case "snapshot" -> "快照";
            case "old_beta" -> "远古 Beta";
            case "old_alpha" -> "远古 Alpha";
            default -> "正式版";
        };
    }

    private Node actionRow(LocalVersions.Installed installed) {
        Button launch = new Button("启动游戏");
        launch.getStyleClass().add("btn-launch");
        launch.setGraphic(dev.yml.ui.component.Icon.of(dev.yml.ui.component.Icon.PLAY, 16, Color.WHITE, true));
        launch.setDisable(installed.broken());
        launch.setOnAction(event -> host.launch(installed));

        Button settings = new Button("游戏设置");
        settings.getStyleClass().add("btn");
        settings.setGraphic(dev.yml.ui.component.Icon.of(dev.yml.ui.component.Icon.SLIDERS, 14, Ui.TEXT));
        settings.setOnAction(event -> host.openInstanceSettings(installed));

        Button folder = new Button("打开目录");
        folder.getStyleClass().add("btn");
        folder.setGraphic(dev.yml.ui.component.Icon.of(dev.yml.ui.component.Icon.FOLDER, 14, Ui.TEXT));
        folder.setOnAction(event -> Util.openFolder(installed.dir()));

        Button favorite = new Button(isFavorite(installed) ? "已收藏" : "收藏");
        favorite.getStyleClass().add("btn");
        favorite.setGraphic(dev.yml.ui.component.Icon.of(dev.yml.ui.component.Icon.PACKAGE, 14, Ui.TEXT));
        favorite.setOnAction(event -> {
            context.toggleFavorite(installed.id());
            refreshList();
            buildDetail();
        });

        HBox row = new HBox(10, launch, settings, folder, favorite);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void exportModpack(LocalVersions.Installed installed) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("导出整合包");
        chooser.setInitialFileName(installed.id().replaceAll("[^A-Za-z0-9._-]", "_") + ".zip");
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("整合包 (*.zip)", "*.zip"));
        java.io.File target = chooser.showSaveDialog(detail.getScene() == null ? null
                : detail.getScene().getWindow());
        if (target == null) {
            return;
        }
        InstanceSettings settings = context.instanceSettings(installed);
        Path gameDir = settings.isolated ? installed.dir() : context.settings.mcPath();
        Path output = target.toPath();
        host.runTask("导出整合包", progress -> {
            progress.note("打包 mods / config / 资源包等");
            try {
                dev.yml.core.content.ModpackExporter.export(installed, gameDir, output, installed.id(), "1.0.0");
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }, () -> host.toast("已导出 " + output.getFileName()));
    }

    private boolean isFavorite(LocalVersions.Installed installed) {
        return context.isFavorite(installed.id());
    }

    private Node infoGrid(LocalVersions.Installed installed) {
        VersionInfo info = installed.info();
        InstanceSettings instance = context.instanceSettings(installed);
        int memory = dev.yml.core.game.MemoryRecommendation.effective(installed, instance);
        boolean memoryAuto = instance.memoryMb <= 0;
        String memoryText = (memory % 1024 == 0 ? (memory / 1024) + " GB" : memory + " MB")
                + (memoryAuto ? " · 自动" : "");
        String javaText;
        if (instance.javaPath != null && !instance.javaPath.isBlank()) {
            javaText = Path.of(instance.javaPath).getFileName().toString();
        } else if (context.settings.defaultJavaPath != null && !context.settings.defaultJavaPath.isBlank()) {
            javaText = "默认 " + Path.of(context.settings.defaultJavaPath).getFileName();
        } else {
            JavaRuntime runtime = info == null ? null : context.javaManager.best(Math.max(8, info.javaMajor));
            javaText = runtime == null ? "未检测到" : runtime.label() + " · " + runtime.vendor;
        }
        Path gameDir = instance.isolated ? installed.dir() : context.settings.mcPath();
        int mods = installed.modCount(context.settings.mcPath(), instance.isolated);

        VBox grid = new VBox(10);
        VBox javaCard = infoCard("Java 运行时", javaText, dev.yml.ui.component.Icon.JAVA);
        javaCard.setOnMouseClicked(event -> host.navigate("settings"));
        clickable(javaCard);
        VBox memoryCard = infoCard("内存", memoryText, dev.yml.ui.component.Icon.SLIDERS);
        memoryCard.setOnMouseClicked(event -> host.openInstanceSettings(installed));
        clickable(memoryCard);
        HBox row1 = new HBox(10, javaCard, memoryCard);
        Node gameDirCard = infoCard("游戏目录", shorten(gameDir.toString(), 42),
                dev.yml.ui.component.Icon.FOLDER);
        if (gameDirCard instanceof VBox gameDirBox) {
            Button openDir = new Button("打开文件夹");
            openDir.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            openDir.setOnAction(event -> Util.openFolder(gameDir));
            gameDirBox.getChildren().add(openDir);
        }
        HBox row2 = new HBox(10, gameDirCard,
                modCard(installed, mods));
        HBox.setHgrow(row1.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(row1.getChildren().get(1), Priority.ALWAYS);
        HBox.setHgrow(row2.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(row2.getChildren().get(1), Priority.ALWAYS);
        grid.getChildren().addAll(row1, row2);
        return grid;
    }

    private Node versionThumb(LocalVersions.Installed installed, double size) {
        StackPane holder = new StackPane();
        holder.setMinSize(size, size);
        holder.setPrefSize(size, size);
        holder.setMaxSize(size, size);
        Path icon = customIcon(gameDir(installed));
        if (icon != null) {
            try {
                javafx.scene.image.Image image = new javafx.scene.image.Image(
                        icon.toUri().toString(), size * 2, size * 2, true, true, true);
                javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(image);
                view.setFitWidth(size);
                view.setFitHeight(size);
                view.setPreserveRatio(true);
                view.setSmooth(true);
                javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(size, size);
                clip.setArcWidth(size * 0.24);
                clip.setArcHeight(size * 0.24);
                holder.setClip(clip);
                holder.getChildren().add(view);
                return holder;
            } catch (Exception ignored) {
            }
        }
        holder.getChildren().add(new CubeIcon(installed.loader(), (int) Math.round(size * 0.8)));
        return holder;
    }

    private static Path customIcon(Path gameDir) {
        for (String ext : new String[]{"png", "jpg", "jpeg"}) {
            Path icon = gameDir.resolve("icon." + ext);
            if (java.nio.file.Files.isRegularFile(icon)) {
                return icon;
            }
        }
        return null;
    }

    private Node modCard(LocalVersions.Installed installed, int mods) {
        VBox card = infoCard("模组", mods + " 个", dev.yml.ui.component.Icon.PACKAGE);
        card.setOnMouseClicked(event -> host.openMods(installed));
        clickable(card);
        return card;
    }

    private void chooseInstanceIcon(LocalVersions.Installed installed) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("选择实例图标");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                "图片 (*.png, *.jpg, *.jpeg)", "*.png", "*.jpg", "*.jpeg"));
        java.io.File file = chooser.showOpenDialog(detail.getScene() == null ? null
                : detail.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            Path dir = gameDir(installed);
            java.nio.file.Files.createDirectories(dir);
            for (String ext : new String[]{"png", "jpg", "jpeg"}) {
                java.nio.file.Files.deleteIfExists(dir.resolve("icon." + ext));
            }
            String name = file.getName().toLowerCase(java.util.Locale.ROOT);
            String ext = name.endsWith(".jpg") || name.endsWith(".jpeg") ? "jpg" : "png";
            java.nio.file.Files.copy(file.toPath(), dir.resolve("icon." + ext),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            refreshList();
            buildDetail();
            host.toast("已设置实例图标（支持 PNG / JPG / JPEG）");
        } catch (Exception e) {
            Util.error(host.window(), "设置图标失败", e.getMessage());
        }
    }

    private static void clickable(Node card) {
        card.getStyleClass().add("card-hover");
        card.setCursor(javafx.scene.Cursor.HAND);
    }

    private VBox infoCard(String title, String value, String iconPath) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card-soft");
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPrefWidth(180);
        card.setMinWidth(0);
        HBox head = Ui.row(7,
                dev.yml.ui.component.Icon.of(iconPath, 13, Ui.TEXT_FAINT),
                Ui.label(title, "tiny", "faint"));
        Label valueLabel = Ui.label(value, "small");
        valueLabel.setTextAlignment(TextAlignment.LEFT);
        card.getChildren().addAll(head, valueLabel);
        return card;
    }

    private Node quickActions(LocalVersions.Installed installed) {
        VBox box = new VBox(10);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(14, 16, 14, 16));
        box.getChildren().add(Ui.label("快捷操作", "section-title"));

        HBox row = new HBox(8);
        row.getChildren().addAll(
                quickButton("键位设置", dev.yml.ui.component.Icon.KEY, () -> host.openKeybinds(installed)),
                quickButton("模组管理", dev.yml.ui.component.Icon.PACKAGE, () -> host.openMods(installed)),
                quickButton("资源包", dev.yml.ui.component.Icon.PACKAGE,
                        () -> host.openResourcePacks(installed)));
        HBox row2 = new HBox(8,
                quickButton("检查更新", dev.yml.ui.component.Icon.REFRESH,
                        () -> host.checkModUpdates(installed)),
                quickButton("崩溃诊断", dev.yml.ui.component.Icon.INFO, () -> host.openDiagnostics(installed)),
                quickButton("实例文件夹", dev.yml.ui.component.Icon.FOLDER,
                        () -> Util.openFolder(gameDir(installed))));
        HBox row3 = new HBox(8,
                quickButton("导出整合包", dev.yml.ui.component.Icon.DOWNLOAD, () -> exportModpack(installed)),
                quickButton("存档管理", dev.yml.ui.component.Icon.FOLDER, () -> {
                    context.select(installed);
                    host.navigate("saves");
                }),
                quickButton("测试启动", dev.yml.ui.component.Icon.REFRESH,
                        () -> host.testLaunch(installed)));
        Button deleteInstance = quickButton("删除当前实例", dev.yml.ui.component.Icon.TRASH,
                () -> deleteInstance(installed));
        deleteInstance.getStyleClass().add("btn-danger");
        HBox row4 = new HBox(8,
                quickButton("实例图标", dev.yml.ui.component.Icon.COPY, () -> chooseInstanceIcon(installed)),
                quickButton("服务器管理", dev.yml.ui.component.Icon.INFO, () -> {
                    context.select(installed);
                    host.navigate("servers");
                }),
                quickButton("光影配置", dev.yml.ui.component.Icon.PACKAGE, () -> {
                    context.select(installed);
                    host.navigate("shader-configs");
                }),
                deleteInstance);
        box.getChildren().addAll(row, row2, row3, row4);
        return box;
    }

    private void deleteInstance(LocalVersions.Installed installed) {
        Path dir = installed.dir().toAbsolutePath().normalize();
        Path versionsRoot = context.settings.mcPath().resolve("versions")
                .toAbsolutePath().normalize();
        if (!dir.startsWith(versionsRoot) || dir.equals(versionsRoot)) {
            Util.error(host.window(), "无法删除", "实例目录不在 versions 下：" + dir);
            return;
        }
        boolean confirmed = Util.confirm(host.window(), "删除当前实例",
                "将删除版本目录：\n" + dir + "\n\n"
                        + "如果该实例使用隔离目录，其中的存档、模组、配置也会一起删除；\n"
                        + "共享目录中的数据不会删除。\n\n"
                        + "此操作不可恢复。",
                "删除实例", "取消");
        if (!confirmed) {
            return;
        }
        try {
            java.util.List<Path> paths;
            try (var stream = java.nio.file.Files.walk(dir)) {
                paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
            }
            for (Path path : paths) {
                java.nio.file.Files.deleteIfExists(path);
            }
            context.refreshVersions();
            host.refreshAll();
            refreshList();
            buildDetail();
            host.toast("已删除实例：" + installed.id());
        } catch (Exception e) {
            Util.error(host.window(), "删除实例失败", e.getMessage());
        }
    }

    private Path gameDir(LocalVersions.Installed installed) {
        InstanceSettings instance = context.instanceSettings(installed);
        return instance.isolated ? installed.dir() : context.settings.mcPath();
    }

    private Button quickButton(String text, String icon, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("btn", "btn-small");
        button.setGraphic(dev.yml.ui.component.Icon.of(icon, 13, Ui.TEXT_DIM));
        button.setWrapText(true);
        button.setMinWidth(118);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setStyle("-fx-font-size: 13px;");
        HBox.setHgrow(button, Priority.ALWAYS);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static String shorten(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return "..." + text.substring(text.length() - max + 3);
    }

    public static String formatDate(String iso) {
        try {
            return DATE.format(Instant.parse(iso));
        } catch (RuntimeException e) {
            return iso == null ? "" : iso;
        }
    }
}
