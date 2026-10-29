package dev.yml.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Small factory helpers that keep page code short. */
public final class Ui {

    public static final Color ACCENT = Color.web("#4c8dff");
    public static final Color TEXT = Color.web("#ececf2");
    public static final Color TEXT_DIM = Color.web("#9a9aa8");
    public static final Color TEXT_FAINT = Color.web("#676774");
    public static final Color GREEN = Color.web("#3ddc97");
    public static final Color RED = Color.web("#ff6259");
    public static final Color YELLOW = Color.web("#f2b84b");
    public static final Color PURPLE = Color.web("#a78bfa");
    public static final Color ORANGE = Color.web("#ff9f45");

    private Ui() {
    }

    public static Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("label");
        return label;
    }

    public static Label label(String text, String... styles) {
        Label label = label(text);
        label.getStyleClass().addAll(styles);
        return label;
    }

    public static Label h1(String text) {
        return label(text, "h1");
    }

    public static Label h2(String text) {
        return label(text, "h2");
    }

    public static Label h3(String text) {
        return label(text, "h3");
    }

    public static Label muted(String text) {
        return label(text, "muted");
    }

    public static Label small(String text) {
        return label(text, "small", "muted");
    }

    public static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        VBox.setVgrow(region, Priority.ALWAYS);
        return region;
    }

    public static HBox row(double spacing, Node... nodes) {
        HBox box = new HBox(spacing, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static VBox column(double spacing, Node... nodes) {
        return new VBox(spacing, nodes);
    }

    public static HBox paddedRow(double spacing, double top, double right, double bottom, double left, Node... nodes) {
        HBox box = row(spacing, nodes);
        box.setPadding(new Insets(top, right, bottom, left));
        return box;
    }

    public static Label chip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("chip");
        return chip;
    }

    public static Label chip(String text, String styleClass) {
        Label chip = chip(text);
        chip.getStyleClass().add(styleClass);
        return chip;
    }

    public static void tooltip(Node node, String text) {
        Tooltip.install(node, new Tooltip(text));
    }

    public static String hex(Color color) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    public static String humanCount(int count) {
        return String.valueOf(count);
    }
}
