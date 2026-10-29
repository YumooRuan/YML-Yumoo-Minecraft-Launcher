package dev.yml.ui.component;

import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Small line/solid icon set rendered from SVG path data (24x24 viewport).
 * Every icon is wrapped in a fixed-size pane so layout is predictable.
 */
public final class Icon {

    public static final String HOME = "M3 11.2 L12 4 L21 11.2 M5.5 9.8 V20 H18.5 V9.8";
    public static final String GRID = "M4 4 H10 V10 H4 Z M14 4 H20 V10 H14 Z M4 14 H10 V20 H4 Z M14 14 H20 V20 H14 Z";
    public static final String DOWNLOAD = "M12 4 V15 M7.5 10.5 L12 15 L16.5 10.5 M4 20 H20";
    public static final String USER = "M12 12.2 A4.1 4.1 0 1 0 12 4 A4.1 4.1 0 1 0 12 12.2 Z M4.4 20.8 C4.4 16.4 7.9 14.8 12 14.8 C16.1 14.8 19.6 16.4 19.6 20.8";
    public static final String TERMINAL = "M3.5 5 H20.5 V19 H3.5 Z M7 9.2 L10 12 L7 14.8 M12.8 14.8 H17";
    public static final String FOLDER = "M3 6.2 H9.3 L11.2 8.4 H21 V19 H3 Z";
    public static final String PLAY = "M8 5 L19 12 L8 19 Z";
    public static final String CLOSE = "M6.5 6.5 L17.5 17.5 M17.5 6.5 L6.5 17.5";
    public static final String MINIMIZE = "M5 12 H19";
    public static final String MAXIMIZE = "M5 5 H19 V19 H5 Z";
    public static final String RESTORE = "M7 7 H17 V17 H7 Z M9.5 4.5 H19.5 V14.5";
    public static final String SEARCH = "M11 4.5 A6.5 6.5 0 1 0 11 17.5 A6.5 6.5 0 1 0 11 4.5 Z M15.8 15.8 L20 20";
    public static final String PLUS = "M12 5 V19 M5 12 H19";
    public static final String CHEVRON_DOWN = "M7 10 L12 15 L17 10";
    public static final String CHEVRON_RIGHT = "M10 7 L15 12 L10 17";
    public static final String CUBE = "M12 3 L21 8 V16 L12 21 L3 16 V8 Z M3 8 L12 13 L21 8 M12 13 V21";
    public static final String JAVA = "M6 8 H16 V15.2 A3.8 3.8 0 0 1 12.2 19 H9.8 A3.8 3.8 0 0 1 6 15.2 Z M16 10.2 H18.2 A2.6 2.6 0 0 1 18.2 15.4 H16";
    public static final String KEY = "M15.2 6.6 A3.5 3.5 0 1 0 15.2 13.6 A3.5 3.5 0 1 0 15.2 6.6 Z M17.7 12.2 L21 15.5 M19.4 13.9 L21.4 11.9";
    public static final String TRASH = "M5 7 H19 M9.2 7 V4.8 H14.8 V7 M7 7 L8 20 H16 L17 7 M10 10.5 V17 M14 10.5 V17";
    public static final String REFRESH = "M19 12 A7 7 0 1 1 12 5 M12 2 V5 H15";
    public static final String CHECK = "M5 12.5 L9.5 17 L19 6.5";
    public static final String PACKAGE = "M12 3 L21 8 V16 L12 21 L3 16 V8 Z M7.5 5.5 L16.5 10.5 V18.5";
    public static final String INFO = "M12 4 A8 8 0 1 0 12 20 A8 8 0 1 0 12 4 Z M12 10.5 V16 M12 7.6 V8.4";
    public static final String STOP = "M6 6 H18 V18 H6 Z";
    public static final String COPY = "M9 9 H19 V19 H9 Z M5 15 V5 H15";
    public static final String NEWS = "M4 5 H20 V19 H4 Z M7.5 9 H16.5 M7.5 12.5 H16.5 M7.5 16 H13";
    public static final String SETTINGS = "M12 9 A3 3 0 1 0 12 15 A3 3 0 1 0 12 9 Z";
    public static final String SLIDERS = "M4 7 H20 M4 12 H20 M4 17 H20 M9 4.8 V9.2 M15 9.8 V14.2 M8 14.8 V19.2";

    private Icon() {
    }

    public static StackPane of(String path, double size, Color color) {
        return of(path, size, color, false);
    }

    public static StackPane of(String path, double size, Color color, boolean filled) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.setStroke(filled ? null : color);
        icon.setStrokeWidth(1.7);
        icon.setStrokeLineCap(StrokeLineCap.ROUND);
        icon.setStrokeLineJoin(StrokeLineJoin.ROUND);
        icon.setFill(filled ? color : Color.TRANSPARENT);
        double scale = size / 24.0;
        icon.setScaleX(scale);
        icon.setScaleY(scale);
        StackPane box = new StackPane(icon);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }

    /** A gear with eight teeth, generated because the outline path would be very long. */
    public static StackPane gear(double size, Color color) {
        Group group = new Group();
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            double x1 = Math.cos(angle) * 5.4;
            double y1 = Math.sin(angle) * 5.4;
            double x2 = Math.cos(angle) * 8.4;
            double y2 = Math.sin(angle) * 8.4;
            SVGPath tooth = new SVGPath();
            tooth.setContent("M" + fmt(x1) + " " + fmt(y1) + " L" + fmt(x2) + " " + fmt(y2));
            tooth.setStroke(color);
            tooth.setStrokeWidth(2.1);
            tooth.setStrokeLineCap(StrokeLineCap.ROUND);
            group.getChildren().add(tooth);
        }
        SVGPath ring = new SVGPath();
        ring.setContent("M12 8.8 A3.2 3.2 0 1 0 12 15.2 A3.2 3.2 0 1 0 12 8.8 Z");
        ring.setStroke(color);
        ring.setStrokeWidth(1.9);
        ring.setFill(Color.TRANSPARENT);
        group.getChildren().add(ring);
        double scale = size / 24.0;
        group.setScaleX(scale);
        group.setScaleY(scale);
        StackPane box = new StackPane(group);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
