package dev.yml.ui;

import dev.yml.core.config.InstanceSettings;
import dev.yml.core.game.LocalVersions;
import dev.yml.core.game.VersionInfo;
import dev.yml.core.java.JavaRuntime;
import dev.yml.ui.component.DialogShell;
import dev.yml.ui.component.KeybindsPane;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/** Per-instance settings: launch options plus the key binding editor. */
public final class InstanceSettingsDialog {

    private final AppContext context;
    private final LocalVersions.Installed version;
    private final Runnable onSave;
    private final DialogShell shell;

    private final TextField memory = new TextField();
    private final javafx.scene.control.Slider memorySlider = new javafx.scene.control.Slider(
            dev.yml.core.game.MemoryRecommendation.MIN_MB, dev.yml.core.game.MemoryRecommendation.MAX_MB, 4096);
    private final CheckBox autoMemory = new CheckBox("自动（按版本推荐）");
    private final Label memoryRecommendLabel = Ui.label("", "tiny", "faint");
    private boolean syncingMemory;
    private int recommendedMemory = 2048;
    private final ComboBox<String> javaChoice = new ComboBox<>();
    private final CheckBox isolated = new CheckBox("版本隔离（每个版本独立存档 / 模组 / 配置）");
    private final TextField width = new TextField();
    private final TextField height = new TextField();
    private final CheckBox fullscreen = new CheckBox("全屏启动");
    private final TextField jvmArgs = new TextField();
    private final TextField gameArgs = new TextField();

    private final List<String> javaPaths = new ArrayList<>();
    private final StackPane tabBody = new StackPane();
    private final Button generalTab = new Button("启动设置");
    private final Button keysTab = new Button("键位设置");
    private final KeybindsPane keybindsPane;
    private InstanceSettings settings;

    public InstanceSettingsDialog(Window owner, AppContext context, LocalVersions.Installed version, Runnable onSave) {
        this.context = context;
        this.version = version;
        this.onSave = onSave;
        this.shell = new DialogShell(owner, "游戏设置 · " + version.id(), 720, 680);
        this.keybindsPane = new KeybindsPane(context, version);
        build();
    }

    private void build() {
        settings = context.instanceSettings(version);
        VersionInfo info = version.info();
        recommendedMemory = dev.yml.core.game.MemoryRecommendation.recommend(version);
        boolean auto = settings.memoryMb <= 0;
        int initial = auto ? recommendedMemory : dev.yml.core.game.MemoryRecommendation.clamp(settings.memoryMb);
        memory.setText(String.valueOf(initial));
        memorySlider.setValue(initial);
        memorySlider.setBlockIncrement(dev.yml.core.game.MemoryRecommendation.STEP_MB);
        memorySlider.setMajorTickUnit(4096);
        memorySlider.setMinorTickCount(3);
        memorySlider.setSnapToTicks(false);
        memoryRecommendLabel.setText("推荐 " + recommendedMemory + " MB（MB 单位，步进 256）");
        autoMemory.setSelected(auto);
        wireMemoryControls();
        applyAutoMemory(auto);
        width.setText(String.valueOf(settings.width));
        height.setText(String.valueOf(settings.height));
        fullscreen.setSelected(settings.fullscreen);
        isolated.setSelected(settings.isolated);
        jvmArgs.setText(String.join(" ", settings.jvmArgs));
        gameArgs.setText(String.join(" ", settings.gameArgs));
        jvmArgs.setPromptText("例如 -XX:+UseG1GC");
        gameArgs.setPromptText("例如 --demo");

        VBox content = new VBox(16);
        content.setPadding(new Insets(2, 4, 8, 2));
        content.getChildren().add(section("内存与 Java",
                formRow("最大内存", memoryWithUnit()),
                formRow("Java 运行时", javaSelector()),
                formRow("", hint(info == null ? "" : "该版本需要 Java " + info.javaMajor))));
        content.getChildren().add(section("窗口",
                formRow("分辨率", resolutionRow()),
                formRow("全屏", fullscreen)));
        content.getChildren().add(section("目录",
                formRow("版本隔离", isolated),
                formRow("游戏目录", hint(version.dir().toString()))));
        content.getChildren().add(section("高级参数",
                formRow("JVM 参数", jvmArgs),
                formRow("游戏参数", gameArgs)));

        ScrollPane general = new ScrollPane(content);
        general.setFitToWidth(true);
        general.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        general.setPadding(new Insets(0));

        VBox keys = new VBox(8, Ui.label("统一管理这个实例的按键设置，可一键应用到其他版本。", "tiny", "faint"),
                keybindsPane);
        VBox.setVgrow(keybindsPane, Priority.ALWAYS);

        tabBody.getChildren().add(general);
        generalTab.getStyleClass().add("tab");
        keysTab.getStyleClass().add("tab");
        generalTab.setOnAction(event -> selectTab("general"));
        keysTab.setOnAction(event -> selectTab("keys"));
        HBox titles = new HBox(6, generalTab, keysTab);

        VBox top = new VBox(10, titles);
        shell.setContent(new VBox(10, top, tabBody));
        VBox.setVgrow(tabBody, Priority.ALWAYS);
        selectTab("general");

        Button cancel = new Button("取消");
        cancel.getStyleClass().add("btn");
        cancel.setOnAction(event -> shell.close());
        Button save = new Button("保存");
        save.getStyleClass().addAll("btn", "btn-primary");
        save.setOnAction(event -> save());
        shell.setFooter(cancel, save);
    }

    private void selectTab(String tab) {
        generalTab.getStyleClass().remove("tab-active");
        keysTab.getStyleClass().remove("tab-active");
        if ("keys".equals(tab)) {
            keysTab.getStyleClass().add("tab-active");
            tabBody.getChildren().setAll((javafx.scene.Node) staticKeys());
        } else {
            generalTab.getStyleClass().add("tab-active");
            tabBody.getChildren().setAll(generalContent());
        }
    }

    private javafx.scene.Node cachedGeneral;
    private javafx.scene.Node cachedKeys;

    private javafx.scene.Node generalContent() {
        if (cachedGeneral == null) {
            cachedGeneral = buildGeneralScroll();
        }
        return cachedGeneral;
    }

    private javafx.scene.Node staticKeys() {
        if (cachedKeys == null) {
            VBox box = new VBox(8, Ui.label("统一管理这个实例的按键设置，可一键应用到其他版本。", "tiny", "faint"),
                    keybindsPane);
            VBox.setVgrow(keybindsPane, Priority.ALWAYS);
            cachedKeys = box;
        }
        return cachedKeys;
    }

    private ScrollPane buildGeneralScroll() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(2, 4, 8, 2));
        VersionInfo info = version.info();
        content.getChildren().add(section("内存与 Java",
                formRow("最大内存", memoryWithUnit()),
                formRow("Java 运行时", javaSelector()),
                formRow("", hint(info == null ? "" : "该版本需要 Java " + info.javaMajor))));
        content.getChildren().add(section("窗口",
                formRow("分辨率", resolutionRow()),
                formRow("全屏", fullscreen)));
        content.getChildren().add(section("目录",
                formRow("版本隔离", isolated),
                formRow("游戏目录", hint(version.dir().toString()))));
        content.getChildren().add(section("高级参数",
                formRow("JVM 参数", jvmArgs),
                formRow("游戏参数", gameArgs)));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private Label hint(String text) {
        Label label = Ui.label(text, "tiny", "faint");
        label.setWrapText(true);
        return label;
    }

    private HBox memoryWithUnit() {
        memory.setPrefWidth(96);
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(6);
        HBox line = new HBox(8, memory, Ui.label("MB", "small", "muted"), autoMemory);
        line.setAlignment(Pos.CENTER_LEFT);
        memorySlider.setPrefWidth(300);
        memorySlider.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().addAll(line, memorySlider, memoryRecommendLabel);
        return new HBox(box);
    }

    private void wireMemoryControls() {
        memorySlider.valueProperty().addListener((obs, old, value) -> {
            if (syncingMemory) {
                return;
            }
            int mb = dev.yml.core.game.MemoryRecommendation.snap(value.intValue());
            syncingMemory = true;
            memory.setText(String.valueOf(mb));
            autoMemory.setSelected(false);
            syncingMemory = false;
        });
        memory.textProperty().addListener((obs, old, value) -> {
            if (syncingMemory) {
                return;
            }
            if (!value.matches("\\d{0,6}")) {
                syncingMemory = true;
                memory.setText(old);
                syncingMemory = false;
                return;
            }
            if (value.isBlank()) {
                return;
            }
            int mb = dev.yml.core.game.MemoryRecommendation.clamp(Integer.parseInt(value));
            syncingMemory = true;
            memorySlider.setValue(mb);
            autoMemory.setSelected(false);
            syncingMemory = false;
        });
        autoMemory.selectedProperty().addListener((obs, old, value) -> {
            if (syncingMemory) {
                return;
            }
            applyAutoMemory(value);
        });
    }

    private void applyAutoMemory(boolean auto) {
        syncingMemory = true;
        if (auto) {
            memory.setText(String.valueOf(recommendedMemory));
            memorySlider.setValue(recommendedMemory);
        }
        memory.setDisable(false);
        memorySlider.setDisable(false);
        syncingMemory = false;
    }

    private HBox resolutionRow() {
        width.setPrefWidth(80);
        height.setPrefWidth(80);
        HBox box = new HBox(6, width, Ui.label("×", "small", "muted"), height);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private ComboBox<String> javaSelector() {
        javaPaths.clear();
        List<String> items = new ArrayList<>();
        items.add("自动选择（推荐）");
        javaPaths.add("");
        for (JavaRuntime runtime : context.javaManager.installed()) {
            items.add(runtime.label() + " · " + runtime.vendor + " · " + runtime.version);
            javaPaths.add(runtime.executable.toString());
        }
        javaChoice.setItems(FXCollections.observableArrayList(items));
        javaChoice.setMaxWidth(Double.MAX_VALUE);
        int index = 0;
        if (settings.javaPath != null && !settings.javaPath.isBlank()) {
            index = Math.max(0, javaPaths.indexOf(settings.javaPath));
        }
        javaChoice.getSelectionModel().select(index);
        return javaChoice;
    }

    private VBox section(String title, javafx.scene.Node... rows) {
        VBox box = new VBox(10);
        box.getStyleClass().add("card-soft");
        box.setPadding(new Insets(14, 16, 14, 16));
        box.getChildren().add(Ui.label(title, "section-title"));
        box.getChildren().addAll(rows);
        return box;
    }

    private GridPane formRow(String label, javafx.scene.Node control) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(96);
        labelColumn.setPrefWidth(96);
        ColumnConstraints controlColumn = new ColumnConstraints();
        controlColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, controlColumn);
        grid.add(Ui.label(label, "small", "muted"), 0, 0);
        grid.add(control, 1, 0);
        GridPane.setHgrow(control, Priority.ALWAYS);
        return grid;
    }

    private void save() {
        if (autoMemory.isSelected()) {
            settings.memoryMb = -1;
        } else {
            int mem = parseInt(memory.getText(), recommendedMemory);
            settings.memoryMb = dev.yml.core.game.MemoryRecommendation.clamp(mem);
        }
        settings.width = Math.max(320, parseInt(width.getText(), 854));
        settings.height = Math.max(240, parseInt(height.getText(), 480));
        settings.fullscreen = fullscreen.isSelected();
        settings.isolated = isolated.isSelected();
        int index = javaChoice.getSelectionModel().getSelectedIndex();
        settings.javaPath = index <= 0 ? "" : javaPaths.get(index);
        settings.jvmArgs.clear();
        for (String token : jvmArgs.getText().trim().split("\\s+")) {
            if (!token.isBlank()) {
                settings.jvmArgs.add(token);
            }
        }
        settings.gameArgs.clear();
        for (String token : gameArgs.getText().trim().split("\\s+")) {
            if (!token.isBlank()) {
                settings.gameArgs.add(token);
            }
        }
        settings.save();
        keybindsPane.save();
        context.settings.save();
        shell.close();
        if (onSave != null) {
            onSave.run();
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public void show() {
        shell.showAndWait();
    }
}
