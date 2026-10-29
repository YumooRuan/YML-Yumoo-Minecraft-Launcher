package dev.yml.ui.page;

import dev.yml.core.admin.AdminAuth;
import dev.yml.core.announce.Announcement;
import dev.yml.core.announce.AnnouncementService;
import dev.yml.ui.AppContext;
import dev.yml.ui.Page;
import dev.yml.ui.Ui;
import dev.yml.ui.UiHost;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/** Admin page: publish, pin and remove announcements (password gated). */
public final class AdminAnnouncementsPage implements Page {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane view = new BorderPane();
    private final TextField title = new TextField();
    private final TextArea content = new TextArea();
    private final CheckBox pinned = new CheckBox("置顶公告");
    private final VBox list = new VBox(6);

    public AdminAnnouncementsPage(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        view.setPadding(new Insets(18, 20, 14, 20));
        HBox header = Ui.row(10, Ui.h1("公告管理"), Ui.small("发布、置顶或删除公告，操作前需验证管理员密码"));
        header.setAlignment(Pos.CENTER_LEFT);

        title.setPromptText("公告标题");
        content.setPromptText("公告正文");
        content.setPrefHeight(80);
        pinned.getStyleClass().add("check-box");
        Button publish = new Button("验证密码并发布公告");
        publish.getStyleClass().addAll("btn", "btn-primary");
        publish.setOnAction(event -> publish());

        VBox form = new VBox(8, title, content, pinned, publish);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(14, 16, 14, 16));

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox center = new VBox(12, form, Ui.label("已发布公告", "section-title"), scroll);
        VBox.setVgrow(center, Priority.ALWAYS);
        view.setTop(new VBox(10, header));
        view.setCenter(center);
    }

    private void publish() {
        String text = title.getText() == null ? "" : title.getText().trim();
        if (text.isBlank()) {
            host.toast("公告标题不能为空");
            return;
        }
        if (!AdminAuth.prompt(host.window(), context.dataRoot, AdminAuth.Action.ANNOUNCEMENT)) {
            host.toast("密码验证未通过，公告未发布");
            return;
        }
        try {
            List<Announcement> all = new ArrayList<>(AnnouncementService.load(context.dataRoot, context.settings));
            all.add(0, Announcement.create(text, content.getText(), pinned.isSelected()));
            AnnouncementService.saveLocal(context.dataRoot, all);
            pushRemote(all);
            title.clear();
            content.clear();
            pinned.setSelected(false);
            render();
            host.toast("公告已发布");
        } catch (Exception e) {
            host.toast("发布失败: " + e.getMessage());
        }
    }

    private void render() {
        list.getChildren().clear();
        List<Announcement> all = AnnouncementService.load(context.dataRoot, context.settings);
        if (all.isEmpty()) {
            list.getChildren().add(Ui.small("还没有发布公告"));
            return;
        }
        for (Announcement announcement : all) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 8, 6, 8));
            row.getStyleClass().add("row-hover");
            if (announcement.pinned()) {
                row.getChildren().add(Ui.chip("置顶", "chip-orange"));
            }
            Label name = Ui.label(announcement.title(), "small");
            name.setMaxWidth(280);
            row.getChildren().addAll(name, Ui.spacer());

            boolean favorite = context.settings.favoriteAnnouncements.contains(announcement.id());
            Button star = new Button(favorite ? "★" : "☆");
            star.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            star.setOnAction(event -> {
                if (context.settings.favoriteAnnouncements.contains(announcement.id())) {
                    context.settings.favoriteAnnouncements.remove(announcement.id());
                } else {
                    context.settings.favoriteAnnouncements.add(announcement.id());
                }
                context.settings.save();
                render();
            });
            Button pin = new Button(announcement.pinned() ? "取消置顶" : "置顶");
            pin.getStyleClass().addAll("btn", "btn-small");
            pin.setOnAction(event -> togglePin(announcement));
            Button delete = new Button("删除");
            delete.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            delete.setOnAction(event -> delete(announcement));
            row.getChildren().addAll(star, pin, delete);
            list.getChildren().add(row);
        }
    }

    private void pushRemote(List<Announcement> all) {
        try {
            AnnouncementService.publishRemote(
                    AnnouncementService.feedUrl(context.settings), all);
            host.toast("公告已同步到线上");
        } catch (Exception e) {
            host.toast("本地已保存，线上同步失败: " + e.getMessage());
        }
    }

    private void togglePin(Announcement announcement) {        if (!AdminAuth.prompt(host.window(), context.dataRoot, AdminAuth.Action.ANNOUNCEMENT)) {
            host.toast("密码验证未通过");
            return;
        }
        try {
            List<Announcement> all = new ArrayList<>(AnnouncementService.load(context.dataRoot, context.settings));
            List<Announcement> updated = new ArrayList<>();
            for (Announcement item : all) {
                updated.add(item.id().equals(announcement.id())
                        ? new Announcement(item.id(), item.title(), item.content(), item.time(), !item.pinned())
                        : item);
            }
            AnnouncementService.saveLocal(context.dataRoot, updated);
            pushRemote(updated);
            render();
        } catch (Exception e) {
            host.toast("修改失败: " + e.getMessage());
        }
    }

    private void delete(Announcement announcement) {
        if (!AdminAuth.prompt(host.window(), context.dataRoot, AdminAuth.Action.ANNOUNCEMENT)) {
            host.toast("密码验证未通过");
            return;
        }
        try {
            List<Announcement> all = new ArrayList<>(AnnouncementService.load(context.dataRoot, context.settings));
            all.removeIf(item -> item.id().equals(announcement.id()));
            AnnouncementService.saveLocal(context.dataRoot, all);
            pushRemote(all);
            render();
        } catch (Exception e) {
            host.toast("删除失败: " + e.getMessage());
        }
    }

    @Override
    public Node view() {
        return view;
    }

    @Override
    public void onShow() {
        render();
    }
}
