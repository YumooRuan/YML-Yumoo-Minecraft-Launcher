package dev.yml.ui.component;

import dev.yml.core.auth.Account;
import dev.yml.core.java.JavaRuntime;
import dev.yml.ui.AppContext;
import dev.yml.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.LinkedHashMap;
import java.util.Map;

/** Left navigation with the account switcher. */
public final class Sidebar extends VBox {

    public interface Listener {
        void onNavigate(String page);

        void onAccountClick();

        default void onLanguage(String code) {
        }

        default void onMoreLanguages() {
        }
    }

    private final AppContext context;
    private final Listener listener;
    private final Map<String, HBox> items = new LinkedHashMap<>();
    private final Label javaLabel;
    private final Label versionLabel;
    private javafx.scene.control.MenuButton languageButton;
    private HBox announcementFavorites;

    public Sidebar(AppContext context, Listener listener) {
        this(context, listener, false);
    }

    public Sidebar(AppContext context, Listener listener, boolean admin) {
        this.context = context;
        this.listener = listener;
        getStyleClass().add("sidebar");
        setPrefWidth(216);
        setMinWidth(216);
        setMaxWidth(216);
        setPadding(new Insets(14, 12, 12, 12));
        setSpacing(6);

        HBox account = buildAccount();

        Label launcherSection = Ui.label("启动", "sidebar-section");
        launcherSection.setPadding(new Insets(6, 0, 4, 12));
        HBox announcementFavorites = navItem("announcement-favorites",
                Icon.of(Icon.FOLDER, 15, Ui.TEXT_FAINT), "公告收藏夹");
        announcementFavorites.getStyleClass().add("nav-sub");
        announcementFavorites.setVisible(false);
        announcementFavorites.setManaged(false);
        this.announcementFavorites = announcementFavorites;

        VBox navigation = new VBox(4);
        navigation.getChildren().addAll(
                launcherSection,
                navItem("home", Icon.of(Icon.HOME, 17, Ui.TEXT_DIM), "主页"),
                navItem("versions", Icon.of(Icon.GRID, 16, Ui.TEXT_DIM), "版本管理"),
                navItem("download", Icon.of(Icon.DOWNLOAD, 17, Ui.TEXT_DIM), "下载"),
                navItem("multiplayer", Icon.of(Icon.INFO, 17, Ui.TEXT_DIM), "联机"),
                navItem("keybinds", Icon.of(Icon.KEY, 17, Ui.TEXT_DIM), "键位设置"),
                navItem("announcements", Icon.of(Icon.INFO, 17, Ui.TEXT_DIM), "公告"),
                announcementFavorites);
        if (admin) {
            Label adminSection = Ui.label("Admin", "sidebar-section");
            adminSection.setPadding(new Insets(14, 0, 4, 12));
            navigation.getChildren().addAll(adminSection,
                    navItem("admin-release", Icon.of(Icon.DOWNLOAD, 17, Ui.TEXT_DIM), "发布新版本"),
                    navItem("admin-announcements", Icon.of(Icon.INFO, 17, Ui.TEXT_DIM), "公告管理"));
        }
        navigation.getChildren().add(navItem("settings", Icon.of(Icon.SLIDERS, 17, Ui.TEXT_DIM), "设置"));
        javafx.scene.control.ScrollPane navigationScroll = new javafx.scene.control.ScrollPane(navigation);
        navigationScroll.setFitToWidth(true);
        navigationScroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        navigationScroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        navigationScroll.getStyleClass().add("sidebar-scroll");
        VBox.setVgrow(navigationScroll, javafx.scene.layout.Priority.ALWAYS);

        VBox footer = new VBox(6);
        footer.setPadding(new Insets(10, 2, 2, 2));
        javaLabel = Ui.label("Java 运行时 -", "tiny", "faint");
        versionLabel = Ui.label("版本 -", "tiny", "faint");
        languageButton = new javafx.scene.control.MenuButton();
        languageButton.getStyleClass().addAll("btn", "btn-small", "btn-ghost", "language-button");
        languageButton.setMaxWidth(Double.MAX_VALUE);
        rebuildLanguageMenu();
        HBox status = new HBox(6,
                Icon.of(Icon.JAVA, 13, Ui.TEXT_FAINT),
                javaLabel,
                Ui.spacer(),
                versionLabel);
        status.setAlignment(Pos.CENTER_LEFT);
        footer.getChildren().addAll(divider(), languageButton, status);
        getChildren().addAll(account, verticalGap(10), navigationScroll, footer);
    }

    /** Debug helper: opens the quick language menu. */
    public void showLanguageMenu() {
        if (languageButton != null) {
            languageButton.show();
        }
    }

    private void rebuildLanguageMenu() {
        languageButton.getItems().clear();
        for (dev.yml.core.i18n.Language.Entry entry : dev.yml.core.i18n.Language.launcherLanguages()) {
            javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(entry.name());
            item.setOnAction(event -> listener.onLanguage(entry.code()));
            languageButton.getItems().add(item);
        }
        languageButton.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        javafx.scene.control.Menu more = new javafx.scene.control.Menu("更多语言…");
        javafx.scene.control.Menu launcherMenu = new javafx.scene.control.Menu("启动器界面");
        for (dev.yml.core.i18n.Language.Entry entry : dev.yml.core.i18n.Language.launcherLanguages()) {
            javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(entry.name());
            item.setOnAction(event -> listener.onLanguage(entry.code()));
            launcherMenu.getItems().add(item);
        }
        javafx.scene.control.Menu gameMenu = new javafx.scene.control.Menu("游戏语言（含彩蛋）");
        for (dev.yml.core.i18n.Language.Entry entry : dev.yml.core.i18n.Language.all()) {
            if (entry.launcher()) {
                continue;
            }
            javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(
                    entry.name() + " · " + entry.code() + (entry.easterEgg() ? "  ✦" : ""));
            item.setOnAction(event -> listener.onLanguage(entry.code()));
            gameMenu.getItems().add(item);
        }
        more.getItems().addAll(launcherMenu, gameMenu);
        languageButton.getItems().add(more);
        refreshLanguage();
    }

    /** The favorites entry only appears while the announcement pages are open. */
    public void setAnnouncementActive(boolean active) {
        if (announcementFavorites == null) {
            return;
        }
        announcementFavorites.setVisible(active);
        announcementFavorites.setManaged(active);
    }

    public void refreshLanguage() {        if (languageButton == null) {
            return;
        }
        dev.yml.core.i18n.Language.Entry entry =
                dev.yml.core.i18n.Language.byCode(context.settings.language);
        languageButton.setText(entry == null ? "简体中文" : entry.name());
    }

    private static Node divider() {        javafx.scene.layout.Region region = new javafx.scene.layout.Region();
        region.getStyleClass().add("divider");
        region.setPrefHeight(1);
        VBox.setMargin(region, new Insets(0, 4, 6, 4));
        return region;
    }

    private static Node verticalGap(double height) {
        javafx.scene.layout.Region region = new javafx.scene.layout.Region();
        region.setMinHeight(height);
        region.setPrefHeight(height);
        return region;
    }

    private HBox buildAccount() {
        Account account = context.account();
        Avatar avatar = new Avatar(account, 34);
        Label name = Ui.label(account.name, "h3");
        Label type = Ui.label(account.type.label(), "tiny", "faint");
        VBox info = new VBox(1, name, type);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);
        HBox box = new HBox(10, avatar, info, Icon.of(Icon.CHEVRON_RIGHT, 14, Ui.TEXT_FAINT));
        box.getStyleClass().add("account-card");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setCursor(Cursor.HAND);
        box.setOnMouseClicked(event -> listener.onAccountClick());
        return box;
    }

    private HBox navItem(String id, javafx.scene.Node icon, String text) {
        Label label = Ui.label(text, "nav-label");
        HBox box = new HBox(11, icon, label);
        box.getStyleClass().add("nav-item");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setCursor(Cursor.HAND);
        box.setUserData(icon);
        box.setOnMouseClicked(event -> {
            setActive(id);
            listener.onNavigate(id);
        });
        items.put(id, box);
        return box;
    }

    public void setActive(String id) {
        for (var entry : items.entrySet()) {
            entry.getValue().getStyleClass().remove("nav-item-active");
            if (entry.getKey().equals(id)) {
                entry.getValue().getStyleClass().add("nav-item-active");
                recolor(entry.getValue(), Ui.ACCENT);
            } else {
                recolor(entry.getValue(), Ui.TEXT_DIM);
            }
        }
    }

    private static void recolor(HBox item, Color color) {
        if (item.getUserData() instanceof javafx.scene.Parent parent
                && !parent.getChildrenUnmodifiable().isEmpty()) {
            javafx.scene.Node inner = parent.getChildrenUnmodifiable().get(0);
            if (inner instanceof javafx.scene.shape.SVGPath path) {
                path.setStroke(color);
            } else if (inner instanceof javafx.scene.Group group) {
                for (javafx.scene.Node child : group.getChildren()) {
                    if (child instanceof javafx.scene.shape.SVGPath path) {
                        path.setStroke(color);
                    }
                }
            }
        }
    }

    public void refreshAccount() {
        HBox account = (HBox) getChildren().get(0);
        Account current = context.account();
        if (account.getChildren().get(0) instanceof Avatar avatar) {
            avatar.update(current);
        }
        if (account.getChildren().get(1) instanceof VBox info) {
            ((Label) info.getChildren().get(0)).setText(current.name);
            ((Label) info.getChildren().get(1)).setText(current.type.label() + " · 点击切换");
        }
    }

    public void updateFooters(int javaCount, int versionCount) {
        javaLabel.setText("Java · " + (javaCount <= 0 ? "未检测" : javaCount + " 个"));
        versionLabel.setText("版本 · " + versionCount);
    }

    public void updateJava(int count) {
        javaLabel.setText("Java · " + (count <= 0 ? "未检测" : count + " 个"));
    }

    public void updateVersions(int count) {
        versionLabel.setText("版本 · " + count);
    }

    public String javaText() {
        JavaRuntime runtime = context.javaManager.best(21);
        return runtime == null ? "未检测到 Java" : runtime.label();
    }
}
