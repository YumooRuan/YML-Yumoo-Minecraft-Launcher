package dev.yml.ui.page;

import dev.yml.core.announce.Announcement;
import dev.yml.core.announce.AnnouncementService;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Published announcements, optionally filtered to the favorites view. */
public final class AnnouncementsPage implements Page {

    private final AppContext context;
    private final UiHost host;
    private final boolean favoritesOnly;
    private final BorderPane view = new BorderPane();
    private final VBox list = new VBox(10);
    private final Label status = Ui.label("", "tiny", "faint");
    private int renderVersion;
    private List<Announcement> loaded;

    public AnnouncementsPage(AppContext context, UiHost host, boolean favoritesOnly) {
        this.context = context;
        this.host = host;
        this.favoritesOnly = favoritesOnly;
        build();
    }

    private void build() {
        view.setPadding(new Insets(18, 20, 14, 20));
        String title = favoritesOnly ? "公告收藏夹" : "公告";
        String subtitle = favoritesOnly ? "你收藏的公告，按发布时间排序" : "启动器公告，置顶优先、其余按时间排序";
        HBox header = Ui.row(10, Ui.h1(title), Ui.small(subtitle));
        header.setAlignment(Pos.CENTER_LEFT);
        Button refresh = new Button("刷新");
        refresh.getStyleClass().addAll("btn", "btn-small");
        refresh.setOnAction(event -> render());
        HBox toolbar = new HBox(10, Ui.label("来源: " + feedDescription(), "tiny", "faint"),
                Ui.spacer(), refresh);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        view.setTop(new VBox(10, header, toolbar));
        view.setCenter(scroll);
        view.setBottom(status);
        BorderPane.setMargin(status, new Insets(8, 0, 0, 2));
    }

    private String feedDescription() {
        String feed = AnnouncementService.feedUrl(context.settings);
        return feed.startsWith("http") ? feed + "（在线）" : feed;
    }

    @Override
    public Node view() {
        return view;
    }

    @Override
    public void onShow() {
        render();
    }

    private void render() {
        int request = ++renderVersion;
        list.getChildren().clear();
        list.getChildren().add(Ui.small("正在同步公告..."));
        status.setText("正在同步在线公告...");
        context.io.submit(() -> {
            List<Announcement> all = AnnouncementService.load(context.dataRoot, context.settings);
            javafx.application.Platform.runLater(() -> {
                if (request != renderVersion) {
                    return;
                }
                loaded = all;
                renderList(all);
            });
        });
    }

    private void renderList(List<Announcement> all) {
        list.getChildren().clear();
        int shown = 0;
        for (Announcement announcement : all) {
            if (favoritesOnly && !context.settings.favoriteAnnouncements.contains(announcement.id())) {
                continue;
            }
            shown++;
            list.getChildren().add(card(announcement));
        }
        if (shown == 0) {
            list.getChildren().add(Ui.small(favoritesOnly ? "还没有收藏任何公告" : "暂无公告"));
            if (!favoritesOnly) {
                Label hint = Ui.small(AnnouncementService.lastStatus().contains("失败")
                        ? "在线公告读取失败，且本地没有缓存。请检查网络或设置中的公告源。"
                        : "在线源当前没有公告。");
                hint.setWrapText(true);
                list.getChildren().add(hint);
            }
        }
        status.setText(shown + " 条公告 · " + AnnouncementService.lastStatus());
    }

    private Node card(Announcement announcement) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14, 16, 14, 16));

        Label title = Ui.h3(announcement.title());
        title.setWrapText(true);
        HBox titleRow = new HBox(8, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        if (announcement.pinned()) {
            titleRow.getChildren().add(Ui.chip("置顶", "chip-orange"));
        }
        titleRow.getChildren().add(Ui.spacer());

        boolean favorite = context.settings.favoriteAnnouncements.contains(announcement.id());
        Button star = new Button(favorite ? "★ 已收藏" : "☆ 收藏");
        star.getStyleClass().addAll("btn", "btn-small");
        star.setOnAction(event -> {
            if (favorite) {
                context.settings.favoriteAnnouncements.remove(announcement.id());
            } else {
                context.settings.favoriteAnnouncements.add(announcement.id());
            }
            context.settings.save();
            if (loaded != null) {
                renderList(loaded);
            } else {
                render();
            }
        });
        titleRow.getChildren().add(star);

        Label content = Ui.label(announcement.content(), "small");
        content.setWrapText(true);
        Label time = Ui.label(formatTime(announcement.time()), "tiny", "faint");
        card.getChildren().addAll(titleRow, content, time);
        return card;
    }

    private static String formatTime(String iso) {
        try {
            Instant instant = Instant.parse(iso);
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault()).format(instant);
        } catch (Exception e) {
            return iso;
        }
    }
}
