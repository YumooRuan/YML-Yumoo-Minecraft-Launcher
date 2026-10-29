package dev.yml.ui;

import dev.yml.core.content.ModUpdate;
import dev.yml.ui.component.DialogShell;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Lets the user pick which mods to update and whether old jars are deleted or disabled. */
public final class ModUpdateDialog {

    public record Plan(List<ModUpdate> updates, boolean keepOld) {
    }

    private final DialogShell shell;
    private final List<ModUpdate> updates;
    private final List<CheckBox> checks = new ArrayList<>();
    private final ToggleGroup policy = new ToggleGroup();
    private final Consumer<Plan> onConfirm;

    public ModUpdateDialog(Window owner, List<ModUpdate> updates, Consumer<Plan> onConfirm) {
        this.updates = updates;
        this.onConfirm = onConfirm;
        this.shell = new DialogShell(owner, "模组更新", 720, 560);
        build();
    }

    private void build() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(0, 6, 6, 0));

        Label summary = Ui.h3("发现 " + updates.size() + " 个可更新的模组");
        Label hint = Ui.label("取消勾选可跳过某个模组。更新文件来自 Modrinth，会校验哈希。", "tiny", "faint");
        hint.setWrapText(true);

        VBox rows = new VBox(4);
        for (ModUpdate update : updates) {
            CheckBox check = new CheckBox();
            check.setSelected(true);
            checks.add(check);
            Label title = Ui.label(update.title(), "small");
            Label versions = Ui.label(update.currentVersion() + "  →  " + update.latestVersion(), "tiny", "faint");
            VBox info = new VBox(2, title, versions);
            HBox row = new HBox(10, check, info);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 10, 8, 10));
            row.getStyleClass().add("row-hover");
            rows.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(280);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        RadioButton replace = new RadioButton("直接替换旧版（删除旧 jar）");
        RadioButton keep = new RadioButton("保留旧版并禁用（旧 jar 重命名为 .jar.disabled）");
        replace.setToggleGroup(policy);
        keep.setToggleGroup(policy);
        replace.setSelected(true);
        replace.getStyleClass().add("check-box");
        keep.getStyleClass().add("check-box");

        content.getChildren().addAll(summary, hint, scroll, Ui.label("更新方式", "section-title"), replace, keep);
        shell.setContent(content);

        Button cancel = new Button("取消");
        cancel.getStyleClass().add("btn");
        cancel.setOnAction(event -> shell.close());
        Button confirm = new Button("开始更新");
        confirm.getStyleClass().addAll("btn", "btn-primary");
        confirm.setOnAction(event -> {
            List<ModUpdate> selected = new ArrayList<>();
            for (int i = 0; i < updates.size(); i++) {
                if (checks.get(i).isSelected()) {
                    selected.add(updates.get(i));
                }
            }
            shell.close();
            if (!selected.isEmpty() && onConfirm != null) {
                onConfirm.accept(new Plan(selected, keep.isSelected()));
            }
        });
        shell.setFooter(cancel, confirm);
        shell.stage.setTitle("模组更新");
    }

    public void show() {
        shell.showAndWait();
    }
}
