package dev.yml.ui;

import dev.yml.core.game.LocalVersions;
import dev.yml.ui.component.DialogShell;
import dev.yml.ui.component.KeybindsPane;
import javafx.scene.control.Button;
import javafx.stage.Window;

/** Standalone key binding dialog (also reachable from the home page quick actions). */
public final class KeybindsDialog {

    private final DialogShell shell;
    private final KeybindsPane pane;

    public KeybindsDialog(Window owner, AppContext context, LocalVersions.Installed version) {
        this.shell = new DialogShell(owner, "键位设置 · " + version.id(), 780, 680);
        this.pane = new KeybindsPane(context, version);
        shell.setContent(pane);

        Button cancel = new Button("取消");
        cancel.getStyleClass().add("btn");
        cancel.setOnAction(event -> shell.close());
        Button save = new Button("保存");
        save.getStyleClass().addAll("btn", "btn-primary");
        save.setOnAction(event -> {
            pane.save();
            shell.close();
            context.settings.save();
        });
        shell.setFooter(cancel, save);
    }

    public void show() {
        shell.showAndWait();
    }
}
