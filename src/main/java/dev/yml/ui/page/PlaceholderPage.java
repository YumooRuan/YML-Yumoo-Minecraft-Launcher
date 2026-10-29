package dev.yml.ui.page;

import dev.yml.ui.AppContext;
import dev.yml.ui.Page;
import dev.yml.ui.Ui;
import dev.yml.ui.UiHost;
import dev.yml.ui.component.Icon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Placeholder used while a page is under construction. */
public class PlaceholderPage implements Page {

    private final Node view;

    public PlaceholderPage(AppContext context, UiHost host, String title, String subtitle, String iconPath) {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.getChildren().addAll(
                Icon.of(iconPath, 56, Ui.TEXT_FAINT),
                Ui.h2(title),
                Ui.small(subtitle));
        Button back = new Button("返回主页");
        back.getStyleClass().add("btn");
        back.setOnAction(event -> host.showPage("home"));
        box.getChildren().add(back);
        StackPane root = new StackPane(box);
        root.setPadding(new Insets(24));
        BorderPane pane = new BorderPane(root);
        this.view = pane;
    }

    @Override
    public Node view() {
        return view;
    }
}
