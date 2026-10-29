package dev.yml.ui;

import dev.yml.core.admin.AdminAuth;
import dev.yml.core.announce.Announcement;
import dev.yml.core.announce.AnnouncementService;
import dev.yml.core.json.Json;
import dev.yml.core.update.ReleaseBuilder;
import dev.yml.ui.component.TitleBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Admin tool: publish launcher versions and announcements, both gated by the admin password. */
public final class AdminWindow {

    private final AppContext context;
    private final Stage stage = new Stage();
    private final TextField version = new TextField("0.1.1");
    private final TextField baseUrl = new TextField("https://your-domain.example/yml");
    private final TextArea notes = new TextArea();
    private final TextArea result = new TextArea();
    private final TextField annTitle = new TextField();
    private final TextArea annContent = new TextArea();
    private final CheckBox annPinned = new CheckBox("置顶公告");
    private final VBox annList = new VBox(6);

    public AdminWindow(AppContext context) {
        this.context = context;
        build();
    }

    private void build() {
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("YML 发布管理");
        stage.getIcons().add(TitleBar.appIcon(32));

        result.setEditable(false);
        result.setPrefHeight(150);
        result.getStyleClass().add("mono");

        Tab versionTab = new Tab("发布新版本", versionPane());
        versionTab.setClosable(false);
        Tab announceTab = new Tab("公告管理", announcementPane());
        announceTab.setClosable(false);
        TabPane tabs = new TabPane(versionTab, announceTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        VBox bottom = new VBox(result);
        bottom.setPadding(new Insets(8, 18, 14, 18));

        BorderPane root = new BorderPane();
        root.setTop(new TitleBar(stage));
        root.setCenter(tabs);
        root.setBottom(bottom);
        root.setStyle("-fx-background-color: #17171c;");
        Scene scene = new Scene(root, 820, 680);
        var css = AdminWindow.class.getResource("/ui/theme.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        ThemeManager.apply(scene, context.settings);
        stage.setScene(scene);
    }

    private VBox versionPane() {
        VBox form = new VBox(10);
        form.setPadding(new Insets(16, 18, 8, 18));
        form.getChildren().addAll(
                Ui.h3("发布新版本"),
                Ui.label("生成发布包与 update.json；发布会要求验证管理员密码。", "tiny", "faint"),
                row("版本号", version),
                row("下载地址前缀", baseUrl),
                Ui.label("更新说明", "tiny", "faint"),
                notes);
        notes.setPrefHeight(70);
        notes.setPromptText("本次更新内容（会显示在更新提示里）");
        Button publish = new Button("验证密码并生成发布包");
        publish.getStyleClass().addAll("btn", "btn-primary");
        publish.setOnAction(event -> generate());
        form.getChildren().add(publish);
        return form;
    }

    private VBox announcementPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(16, 18, 8, 18));
        annTitle.setPromptText("公告标题");
        annContent.setPromptText("公告正文");
        annContent.setPrefHeight(90);
        annPinned.getStyleClass().add("check-box");
        Button publish = new Button("验证密码并发布公告");
        publish.getStyleClass().addAll("btn", "btn-primary");
        publish.setOnAction(event -> publishAnnouncement());

        VBox form = new VBox(8, Ui.h3("发布公告"), annTitle, annContent, annPinned, publish);
        Label listTitle = Ui.label("已发布公告（置顶可切换，收藏与普通端同步）", "tiny", "faint");
        pane.getChildren().addAll(form, listTitle, annList);
        refreshAnnouncements();
        return pane;
    }

    private void publishAnnouncement() {
        String title = annTitle.getText() == null ? "" : annTitle.getText().trim();
        String content = annContent.getText() == null ? "" : annContent.getText().trim();
        if (title.isBlank()) {
            result.setText("公告标题不能为空");
            return;
        }
        if (!AdminAuth.prompt(stage, context.dataRoot, dev.yml.core.admin.AdminAuth.Action.ANNOUNCEMENT)) {
            result.setText("密码验证未通过，公告未发布。");
            return;
        }
        try {
            List<Announcement> list = new ArrayList<>(AnnouncementService.load(context.dataRoot, context.settings));
            list.add(0, Announcement.create(title, content, annPinned.isSelected()));
            AnnouncementService.saveLocal(context.dataRoot, list);
            annTitle.clear();
            annContent.clear();
            annPinned.setSelected(false);
            refreshAnnouncements();
            result.setText("公告已发布：" + title + "\n"
                    + "本地: " + context.dataRoot.resolve("announcements.json") + "\n"
                    + "发布副本: " + context.dataRoot.resolve("releases").resolve("announcements.json"));
        } catch (Exception e) {
            result.setText("发布公告失败: " + e.getMessage());
        }
    }

    private void refreshAnnouncements() {
        annList.getChildren().clear();
        List<Announcement> list = AnnouncementService.load(context.dataRoot, context.settings);
        if (list.isEmpty()) {
            annList.getChildren().add(Ui.small("还没有发布公告"));
            return;
        }
        for (Announcement announcement : list) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 8, 6, 8));
            row.getStyleClass().add("row-hover");
            Label title = Ui.label(announcement.title(), "small");
            title.setMaxWidth(260);
            if (announcement.pinned()) {
                row.getChildren().add(Ui.chip("置顶", "chip-orange"));
            }
            row.getChildren().addAll(title, Ui.spacer());

            Button pin = new Button(announcement.pinned() ? "取消置顶" : "置顶");
            pin.getStyleClass().addAll("btn", "btn-small");
            pin.setOnAction(event -> togglePin(announcement));
            boolean favorite = context.settings.favoriteAnnouncements.contains(announcement.id());
            Button star = new Button(favorite ? "★" : "☆");
            star.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            star.setOnAction(event -> toggleFavorite(announcement));
            Button delete = new Button("删除");
            delete.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            delete.setOnAction(event -> delete(announcement));
            row.getChildren().addAll(star, pin, delete);
            annList.getChildren().add(row);
        }
    }

    private void toggleFavorite(Announcement announcement) {
        if (context.settings.favoriteAnnouncements.contains(announcement.id())) {
            context.settings.favoriteAnnouncements.remove(announcement.id());
        } else {
            context.settings.favoriteAnnouncements.add(announcement.id());
        }
        context.settings.save();
        refreshAnnouncements();
    }

    private void togglePin(Announcement announcement) {
        if (!AdminAuth.prompt(stage, context.dataRoot, dev.yml.core.admin.AdminAuth.Action.ANNOUNCEMENT)) {
            result.setText("密码验证未通过，置顶状态未修改。");
            return;
        }
        try {
            List<Announcement> list = new ArrayList<>(AnnouncementService.load(context.dataRoot, context.settings));
            List<Announcement> updated = new ArrayList<>();
            for (Announcement item : list) {
                updated.add(item.id().equals(announcement.id())
                        ? new Announcement(item.id(), item.title(), item.content(), item.time(), !item.pinned())
                        : item);
            }
            AnnouncementService.saveLocal(context.dataRoot, updated);
            refreshAnnouncements();
            result.setText("已更新公告置顶状态。");
        } catch (Exception e) {
            result.setText("修改失败: " + e.getMessage());
        }
    }

    private void delete(Announcement announcement) {
        if (!AdminAuth.prompt(stage, context.dataRoot, dev.yml.core.admin.AdminAuth.Action.ANNOUNCEMENT)) {
            result.setText("密码验证未通过，公告未删除。");
            return;
        }
        try {
            List<Announcement> list = new ArrayList<>(AnnouncementService.load(context.dataRoot, context.settings));
            list.removeIf(item -> item.id().equals(announcement.id()));
            AnnouncementService.saveLocal(context.dataRoot, list);
            refreshAnnouncements();
            result.setText("已删除公告：" + announcement.title());
        } catch (Exception e) {
            result.setText("删除失败: " + e.getMessage());
        }
    }

    private HBox row(String label, TextField field) {
        Label name = Ui.label(label, "small", "muted");
        name.setMinWidth(120);
        HBox box = new HBox(10, name, field);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        return box;
    }

    private Path installDir() {
        try {
            Path location = Path.of(AdminWindow.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                return location.getParent();
            }
        } catch (Exception ignored) {
        }
        Path dist = Path.of(System.getProperty("user.dir"), "dist", "YML");
        return Files.isDirectory(dist) ? dist : Path.of(System.getProperty("user.dir"));
    }

    private void generate() {
        if (!AdminAuth.prompt(stage, context.dataRoot, dev.yml.core.admin.AdminAuth.Action.VERSION)) {
            result.setText("密码验证未通过，未生成发布包。");
            return;
        }
        try {
            String ver = version.getText().trim();
            String urlBase = baseUrl.getText().trim();
            if (!ver.matches("[0-9]+(\\.[0-9]+){1,3}")) {
                throw new IllegalArgumentException("版本号格式应为 0.1.1");
            }
            Path install = installDir();
            Path outDir = context.dataRoot.resolve("releases");
            Path zip = outDir.resolve("yml-" + ver + ".zip");
            ReleaseBuilder.zip(install, zip);
            String url = (urlBase.endsWith("/") ? urlBase : urlBase + "/") + zip.getFileName();
            var manifest = ReleaseBuilder.manifest(ver, url, zip, notes.getText());
            Path json = outDir.resolve("update.json");
            ReleaseBuilder.writeManifest(json, manifest);
            result.setText("安装目录: " + install + "\n"
                    + "发布包: " + zip + "\n"
                    + "清单: " + json + "\n\n"
                    + Json.write(manifest, true) + "\n\n"
                    + "把 yml-" + ver + ".zip 和 update.json 上传到 " + urlBase
                    + "，并在启动器设置里填写 update.json 的完整地址。");
        } catch (Exception e) {
            result.setText("生成失败: " + e.getMessage());
        }
    }

    public void show() {
        stage.show();
    }
}
