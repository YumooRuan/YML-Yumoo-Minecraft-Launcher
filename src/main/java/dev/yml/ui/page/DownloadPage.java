package dev.yml.ui.page;

import dev.yml.ui.AppContext;
import dev.yml.ui.Page;
import dev.yml.ui.Ui;
import dev.yml.ui.UiHost;
import dev.yml.ui.page.download.DownloadTab;
import dev.yml.ui.page.download.JavaTab;
import dev.yml.ui.page.download.LoaderTab;
import dev.yml.ui.page.download.VersionTab;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/** Download center with version / loader / Java tabs. */
public final class DownloadPage implements Page {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane view = new BorderPane();
    private final StackPane body = new StackPane();
    private final Map<String, DownloadTab> tabs = new LinkedHashMap<>();
    private final Map<String, Button> tabButtons = new LinkedHashMap<>();
    private String current = "versions";
    private dev.yml.ui.page.download.ContentTab contentTab;

    public DownloadPage(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        view.setPadding(new Insets(18, 20, 14, 20));

        javafx.scene.control.ComboBox<String> sourceBox = new javafx.scene.control.ComboBox<>();
        for (dev.yml.core.net.Mirror.Source source : dev.yml.core.net.Mirror.Source.values()) {
            sourceBox.getItems().add(source.label());
        }
        dev.yml.core.net.Mirror.Source currentSource = context.source();
        sourceBox.getSelectionModel().select(currentSource.ordinal());
        sourceBox.setPrefWidth(190);
        sourceBox.setOnAction(event -> {
            int index = sourceBox.getSelectionModel().getSelectedIndex();
            if (index < 0) {
                return;
            }
            dev.yml.core.net.Mirror.Source picked = dev.yml.core.net.Mirror.Source.values()[index];
            context.settings.downloadSource = picked.id();
            context.settings.save();
            DownloadTab tab = tabs.get(current);
            if (tab != null) {
                tab.refresh();
            }
            host.toast("下载源已切换为 " + picked.label());
        });
        HBox title = Ui.row(10, Ui.h1("下载"), Ui.small("安装游戏版本、模组加载器与 Java 运行时"),
                Ui.spacer(), Ui.label("下载源", "small", "muted"), sourceBox);
        title.setAlignment(Pos.CENTER_LEFT);

        HBox tabBar = new HBox(6);
        register("versions", "游戏版本", new VersionTab(context, host));
        register("loaders", "模组加载器", new LoaderTab(context, host));
        contentTab = new dev.yml.ui.page.download.ContentTab(context, host);
        register("content", "内容下载", contentTab);
        register("java", "Java 运行时", new JavaTab(context, host));
        Button resourcePacks = new Button("资源包");
        resourcePacks.getStyleClass().addAll("btn", "btn-small");
        resourcePacks.setOnAction(event -> {
            contentTab.prepareType("resourcepack");
            select("content");
        });
        Button shaders = new Button("光影");
        shaders.getStyleClass().addAll("btn", "btn-small");
        shaders.setOnAction(event -> {
            contentTab.prepareType("shader");
            select("content");
        });
        for (Button button : tabButtons.values()) {
            tabBar.getChildren().add(button);
        }
        tabBar.getChildren().addAll(Ui.spacer(), resourcePacks, shaders);

        VBox top = new VBox(12, title, tabBar);
        view.setTop(top);
        view.setCenter(body);
        select("versions");
    }

    private void register(String id, String label, DownloadTab tab) {
        tabs.put(id, tab);
        Button button = new Button(label);
        button.getStyleClass().addAll("tab");
        button.setOnAction(event -> select(id));
        tabButtons.put(id, button);
    }

    public void selectContentType(String id) {
        if (contentTab != null) {
            contentTab.prepareType(id);
        }
        select("content");
    }

    public void select(String id) {
        current = id;
        for (var entry : tabButtons.entrySet()) {
            entry.getValue().getStyleClass().remove("tab-active");
            if (entry.getKey().equals(id)) {
                entry.getValue().getStyleClass().add("tab-active");
            }
        }
        DownloadTab tab = tabs.get(id);
        body.getChildren().setAll(tab.view());
        tab.onShow();
    }

    @Override
    public Node view() {
        return view;
    }

    @Override
    public void onShow() {
        DownloadTab tab = tabs.get(current);
        if (tab != null) {
            tab.onShow();
        }
    }
}
