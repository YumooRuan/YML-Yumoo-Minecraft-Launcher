package dev.yml.ui.component;

import dev.yml.core.game.LoaderType;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

/**
 * Loader-specific version icon drawn as vector art:
 * grass block for vanilla, anvil for Forge/NeoForge, woven fabric for Fabric/Quilt.
 */
public final class CubeIcon extends Group {

    private static final double[] TOP = {12, 2.6, 20.6, 7.4, 12, 12.2, 3.4, 7.4};
    private static final double[] LEFT = {3.4, 7.4, 12, 12.2, 12, 21.4, 3.4, 16.6};
    private static final double[] RIGHT = {20.6, 7.4, 12, 12.2, 12, 21.4, 20.6, 16.6};
    private static final double[] OUTLINE = {12, 2.6, 20.6, 7.4, 20.6, 16.6, 12, 21.4, 3.4, 16.6, 3.4, 7.4, 12, 2.6};

    public CubeIcon(LoaderType loader, double size) {
        double s = size / 24.0;
        Group art = switch (loader) {
            case VANILLA -> grassBlock();
            case FORGE -> anvil(false);
            case NEOFORGE -> anvil(true);
            case FABRIC -> weave(false);
            case QUILT -> weave(true);
            case OPTIFINE -> optifine();
            default -> plainCube();
        };
        art.getTransforms().add(new Scale(s, s));
        art.setMouseTransparent(true);
        getChildren().add(art);
    }

    private static Group grassBlock() {
        Group group = new Group();
        group.getChildren().addAll(
                polygon("#6fbf4a", TOP),
                polygon("#8a5a3b", LEFT),
                polygon("#6e452e", RIGHT),
                polygon("#5da33c", new double[]{3.4, 7.4, 12, 12.2, 12, 14.3, 3.4, 9.5}),
                polygon("#5da33c", new double[]{20.6, 7.4, 12, 12.2, 12, 14.3, 20.6, 9.5}),
                polygon("#7fcf57", new double[]{8.1, 5.1, 10.4, 6.3, 9.4, 7.5, 7.0, 6.3}),
                polygon("#5da33c", new double[]{13.2, 6.4, 15.3, 7.5, 14.0, 8.8, 12.0, 7.6}),
                outline());
        return group;
    }

    private static Group anvil(boolean neo) {
        Group group = new Group();
        Color light = Color.web(neo ? "#f2b47a" : "#c3cbd8");
        Color mid = Color.web(neo ? "#d48c4a" : "#929dad");
        Color dark = Color.web(neo ? "#a8652f" : "#687385");
        Color edge = Color.web(neo ? "#6f3e1c" : "#3e4755");
        group.getChildren().addAll(
                path("M5.2 6.1 L2.2 7.8 L5.2 9.1 Z", mid, edge, 0.7),
                path("M4.6 4.8 H19.6 L17.9 8.2 H6.2 Z", light, edge, 0.7),
                path("M9.0 8.2 H15.4 L14.5 12.1 H10.0 Z", mid, edge, 0.7),
                path("M6.1 13.5 H18.1 L20.0 17.0 H4.2 Z", dark, edge, 0.7),
                path("M5.0 17.0 H19.2 L18.0 20.2 H6.1 Z", mid, edge, 0.7),
                path("M6.5 6.0 H18.4", Color.TRANSPARENT, light, 0.9));
        return group;
    }

    private static Group weave(boolean quiltStyle) {
        Group group = new Group();
        String base = quiltStyle ? "#d9c2e8" : "#e9d6ae";
        String thread = quiltStyle ? "#8869a8" : "#7d5a34";
        String accent = quiltStyle ? "#b18fd0" : "#c9a56b";
        group.getChildren().addAll(
                path("M4 3 H20 Q22 3 22 5 V19 Q22 21 20 21 H4 Q2 21 2 19 V5 Q2 3 4 3 Z",
                        Color.web(base), Color.web(thread, 0.55), 0.7),
                path("M7 4 V20", Color.TRANSPARENT, Color.web(thread), 1.1),
                path("M12 3 V21", Color.TRANSPARENT, Color.web(accent), 1.1),
                path("M17 4 V20", Color.TRANSPARENT, Color.web(thread), 1.1),
                path("M3 8 H21", Color.TRANSPARENT, Color.web(thread), 1.0),
                path("M3 12 H21", Color.TRANSPARENT, Color.web(accent), 1.2),
                path("M3 16 H21", Color.TRANSPARENT, Color.web(thread), 1.0),
                path("M4.5 19.5 L19.5 4.5", Color.TRANSPARENT, Color.web(thread, 0.65), 0.8));
        return group;
    }

    private static Group optifine() {
        Group group = new Group();
        group.getChildren().addAll(
                polygon("#8fd0ff", TOP),
                polygon("#4f93c8", LEFT),
                polygon("#3b7fae", RIGHT),
                polygon("#e7f5ff", new double[]{9.0, 6.4, 12.0, 8.0, 10.2, 9.8, 7.2, 8.2}),
                polygon("#bfe4ff", new double[]{13.2, 7.4, 16.4, 9.1, 14.4, 10.8, 11.2, 9.1}),
                outline());
        return group;
    }

    private static Group plainCube() {
        Group group = new Group();
        group.getChildren().addAll(
                polygon("#8e8e9c", TOP),
                polygon("#5f5f6d", LEFT),
                polygon("#74748a", RIGHT),
                outline());
        return group;
    }

    private static SVGPath polygon(String fill, double[] xy) {
        SVGPath polygon = new SVGPath();
        polygon.setContent(points(xy));
        polygon.setFill(Color.web(fill));
        return polygon;
    }

    private static SVGPath outline() {
        SVGPath outline = new SVGPath();
        outline.setContent(points(OUTLINE));
        outline.setFill(Color.TRANSPARENT);
        outline.setStroke(Color.rgb(0, 0, 0, 0.32));
        outline.setStrokeWidth(0.8);
        return outline;
    }

    private static SVGPath path(String content, Color fill, Color stroke, double width) {
        SVGPath path = new SVGPath();
        path.setContent(content);
        path.setFill(fill);
        path.setStroke(stroke);
        path.setStrokeWidth(width);
        path.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        return path;
    }

    private static String points(double[] xy) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < xy.length; i += 2) {
            sb.append(i == 0 ? "M" : " L")
                    .append(fmt(xy[i])).append(' ').append(fmt(xy[i + 1]));
        }
        sb.append(" Z");
        return sb.toString();
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
