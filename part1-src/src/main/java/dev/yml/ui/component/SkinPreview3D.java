package dev.yml.ui.component;

import javafx.animation.AnimationTimer;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;

import java.util.ArrayList;
import java.util.List;

/**
 * Textured 3D player preview with a running animation (limbs rotate around their joints),
 * drag-to-rotate and wheel zoom.
 */
public final class SkinPreview3D extends StackPane {

    private static final double SCALE = 1.6;
    private static final int HEAD = 0;
    private static final int TORSO = 1;
    private static final int ARM_R = 2;
    private static final int ARM_L = 3;
    private static final int LEG_R = 4;
    private static final int LEG_L = 5;

    private final Group model = new Group();
    private final Rotate spin = new Rotate(0, Rotate.Y_AXIS);
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final MeshView meshView = new MeshView();
    private TriangleMesh mesh;
    private final List<Integer> partTags = new ArrayList<>();
    private float[] basePoints;
    private Image skin;

    private AnimationTimer runner;
    private double cameraDistance = 190;
    private double time;
    private boolean dragging;
    private double dragStartX;
    private double dragStartAngle;

    public SkinPreview3D(double width, double height) {
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
        setPickOnBounds(true);

        meshView.setCullFace(CullFace.NONE);
        model.getChildren().add(meshView);
        model.getTransforms().add(spin);
        model.setScaleX(SCALE);
        model.setScaleY(SCALE);
        model.setScaleZ(SCALE);
        model.setTranslateY(16 * SCALE);

        camera.setNearClip(0.1);
        camera.setFarClip(3000);
        camera.setFieldOfView(30);
        camera.setTranslateZ(-cameraDistance);

        AmbientLight ambient = new AmbientLight(Color.rgb(90, 90, 95));
        PointLight key = new PointLight(Color.rgb(170, 170, 175));
        key.setTranslateX(-80);
        key.setTranslateY(-120);
        key.setTranslateZ(-160);
        PointLight fill = new PointLight(Color.rgb(55, 55, 70));
        fill.setTranslateX(90);
        fill.setTranslateY(30);
        fill.setTranslateZ(-120);

        Group root = new Group(model, ambient, key, fill);
        SubScene subScene = new SubScene(root, width, height, true, SceneAntialiasing.BALANCED);
        subScene.setCamera(camera);
        subScene.setFill(Color.TRANSPARENT);
        getChildren().add(subScene);

        setOnMousePressed(event -> {
            dragging = true;
            dragStartX = event.getSceneX();
            dragStartAngle = spin.getAngle();
        });
        setOnMouseDragged(event -> {
            if (dragging) {
                spin.setAngle(dragStartAngle + (event.getSceneX() - dragStartX) * 0.6);
            }
        });
        setOnMouseReleased(event -> dragging = false);
        setOnScroll(event -> {
            cameraDistance = Math.max(90, Math.min(420, cameraDistance - event.getDeltaY() * 0.6));
            camera.setTranslateZ(-cameraDistance);
        });
    }

    public void setSkin(Image image) {
        this.skin = image;
        if (image == null || image.getWidth() < 64 || image.getHeight() < 32) {
            return;
        }
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseMap(image);
        material.setSpecularColor(Color.rgb(20, 20, 20));
        meshView.setMaterial(material);
        buildMesh();
        startRunner();
    }

    private void buildMesh() {
        partTags.clear();
        List<Float> points = new ArrayList<>();
        List<Float> uvs = new ArrayList<>();
        List<Integer> faces = new ArrayList<>();

        addPart(points, uvs, faces, HEAD, 0, -28, 0, 8, 8, 8,
                new int[]{8, 0, 8, 8}, new int[]{16, 0, 8, 8}, new int[]{0, 8, 8, 8},
                new int[]{8, 8, 8, 8}, new int[]{16, 8, 8, 8}, new int[]{24, 8, 8, 8});
        addPart(points, uvs, faces, TORSO, 0, -18, 0, 8, 12, 4,
                new int[]{20, 16, 8, 4}, new int[]{28, 16, 8, 4}, new int[]{16, 20, 4, 12},
                new int[]{20, 20, 8, 12}, new int[]{28, 20, 4, 12}, new int[]{32, 20, 8, 12});
        addPart(points, uvs, faces, ARM_R, -6, -18, 0, 4, 12, 4,
                new int[]{44, 16, 4, 4}, new int[]{48, 16, 4, 4}, new int[]{40, 20, 4, 12},
                new int[]{44, 20, 4, 12}, new int[]{48, 20, 4, 12}, new int[]{52, 20, 4, 12});
        addPart(points, uvs, faces, ARM_L, 6, -18, 0, 4, 12, 4,
                new int[]{36, 48, 4, 4}, new int[]{40, 48, 4, 4}, new int[]{32, 52, 4, 12},
                new int[]{36, 52, 4, 12}, new int[]{40, 52, 4, 12}, new int[]{44, 52, 4, 12});
        addPart(points, uvs, faces, LEG_R, -2, -6, 0, 4, 12, 4,
                new int[]{4, 16, 4, 4}, new int[]{8, 16, 4, 4}, new int[]{0, 20, 4, 12},
                new int[]{4, 20, 4, 12}, new int[]{8, 20, 4, 12}, new int[]{12, 20, 4, 12});
        addPart(points, uvs, faces, LEG_L, 2, -6, 0, 4, 12, 4,
                new int[]{20, 48, 4, 4}, new int[]{24, 48, 4, 4}, new int[]{16, 52, 4, 12},
                new int[]{20, 52, 4, 12}, new int[]{24, 52, 4, 12}, new int[]{28, 52, 4, 12});

        mesh = new TriangleMesh();
        float[] pointArray = new float[points.size()];
        for (int i = 0; i < pointArray.length; i++) {
            pointArray[i] = points.get(i);
        }
        float[] uvArray = new float[uvs.size()];
        for (int i = 0; i < uvArray.length; i++) {
            uvArray[i] = uvs.get(i);
        }
        int[] faceArray = new int[faces.size()];
        for (int i = 0; i < faceArray.length; i++) {
            faceArray[i] = faces.get(i);
        }
        mesh.getPoints().setAll(pointArray);
        mesh.getTexCoords().setAll(uvArray);
        mesh.getFaces().setAll(faceArray);
        meshView.setMesh(mesh);
        basePoints = pointArray.clone();
    }

    private void addPart(List<Float> points, List<Float> uvs, List<Integer> faces, int part,
                         double cx, double cy, double cz, double w, double h, double d,
                         int[] top, int[] bottom, int[] right, int[] front, int[] left, int[] back) {
        addFace(points, uvs, faces, part, front,
                cx - w / 2, cy - h / 2, cz + d / 2,
                cx + w / 2, cy - h / 2, cz + d / 2,
                cx + w / 2, cy + h / 2, cz + d / 2,
                cx - w / 2, cy + h / 2, cz + d / 2);
        addFace(points, uvs, faces, part, back,
                cx + w / 2, cy - h / 2, cz - d / 2,
                cx - w / 2, cy - h / 2, cz - d / 2,
                cx - w / 2, cy + h / 2, cz - d / 2,
                cx + w / 2, cy + h / 2, cz - d / 2);
        addFace(points, uvs, faces, part, right,
                cx + w / 2, cy - h / 2, cz + d / 2,
                cx + w / 2, cy - h / 2, cz - d / 2,
                cx + w / 2, cy + h / 2, cz - d / 2,
                cx + w / 2, cy + h / 2, cz + d / 2);
        addFace(points, uvs, faces, part, left,
                cx - w / 2, cy - h / 2, cz - d / 2,
                cx - w / 2, cy - h / 2, cz + d / 2,
                cx - w / 2, cy + h / 2, cz + d / 2,
                cx - w / 2, cy + h / 2, cz - d / 2);
        addFace(points, uvs, faces, part, top,
                cx - w / 2, cy - h / 2, cz - d / 2,
                cx + w / 2, cy - h / 2, cz - d / 2,
                cx + w / 2, cy - h / 2, cz + d / 2,
                cx - w / 2, cy - h / 2, cz + d / 2);
        addFace(points, uvs, faces, part, bottom,
                cx - w / 2, cy + h / 2, cz + d / 2,
                cx + w / 2, cy + h / 2, cz + d / 2,
                cx + w / 2, cy + h / 2, cz - d / 2,
                cx - w / 2, cy + h / 2, cz - d / 2);
    }

    private void addFace(List<Float> points, List<Float> uvs, List<Integer> faces, int part, int[] rect,
                         double x0, double y0, double z0, double x1, double y1, double z1,
                         double x2, double y2, double z2, double x3, double y3, double z3) {
        int base = points.size() / 3;
        points.add((float) x0);
        points.add((float) y0);
        points.add((float) z0);
        points.add((float) x1);
        points.add((float) y1);
        points.add((float) z1);
        points.add((float) x2);
        points.add((float) y2);
        points.add((float) z2);
        points.add((float) x3);
        points.add((float) y3);
        points.add((float) z3);
        for (int i = 0; i < 4; i++) {
            partTags.add(part);
        }

        float u0 = rect[0] / 64f;
        float v0 = rect[1] / 64f;
        float u1 = (rect[0] + rect[2]) / 64f;
        float v1 = (rect[1] + rect[3]) / 64f;
        int uvBase = uvs.size() / 2;
        uvs.add(u0);
        uvs.add(v0);
        uvs.add(u1);
        uvs.add(v0);
        uvs.add(u1);
        uvs.add(v1);
        uvs.add(u0);
        uvs.add(v1);

        faces.add(base);
        faces.add(uvBase);
        faces.add(base + 1);
        faces.add(uvBase + 1);
        faces.add(base + 2);
        faces.add(uvBase + 2);
        faces.add(base);
        faces.add(uvBase);
        faces.add(base + 2);
        faces.add(uvBase + 2);
        faces.add(base + 3);
        faces.add(uvBase + 3);
    }

    private void startRunner() {
        if (runner != null) {
            runner.stop();
        }
        runner = new AnimationTimer() {
            private long last;

            @Override
            public void handle(long now) {
                if (last == 0) {
                    last = now;
                    return;
                }
                double delta = (now - last) / 1_000_000_000.0;
                last = now;
                time += delta;
                animateLimbs(Math.sin(time * 7.5) * 42);
                if (!dragging) {
                    spin.setAngle(spin.getAngle() + delta * 16);
                }
            }
        };
        runner.start();
    }

    private void animateLimbs(double swing) {
        if (mesh == null || basePoints == null) {
            return;
        }
        float[] points = basePoints.clone();
        for (int i = 0; i < partTags.size(); i++) {
            int part = partTags.get(i);
            double angle;
            double pivotY;
            double pivotX;
            switch (part) {
                case ARM_R -> {
                    angle = swing;
                    pivotY = -24;
                    pivotX = -6;
                }
                case ARM_L -> {
                    angle = -swing;
                    pivotY = -24;
                    pivotX = 6;
                }
                case LEG_R -> {
                    angle = -swing * 0.9;
                    pivotY = -12;
                    pivotX = -2;
                }
                case LEG_L -> {
                    angle = swing * 0.9;
                    pivotY = -12;
                    pivotX = 2;
                }
                case HEAD -> {
                    points[i * 3 + 1] = (float) (basePoints[i * 3 + 1]
                            - Math.abs(Math.sin(time * 7.5)) * 1.0);
                    continue;
                }
                default -> {
                    continue;
                }
            }
            double y = basePoints[i * 3 + 1] - pivotY;
            double z = basePoints[i * 3 + 2];
            double radians = Math.toRadians(angle);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            points[i * 3] = basePoints[i * 3];
            points[i * 3 + 1] = (float) (pivotY + y * cos - z * sin);
            points[i * 3 + 2] = (float) (y * sin + z * cos);
        }
        mesh.getPoints().setAll(points);
    }

    public Image skin() {
        return skin;
    }

    /** Stops the running animation so hidden pages do not keep rendering. */
    public void stop() {
        if (runner != null) {
            runner.stop();
            runner = null;
        }
    }
}
