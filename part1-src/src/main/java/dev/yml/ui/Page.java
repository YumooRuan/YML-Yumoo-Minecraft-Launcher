package dev.yml.ui;

import javafx.scene.Node;

/** A top-level content page shown in the main window. */
public interface Page {

    Node view();

    default void onShow() {
    }

    /** Called before another page replaces this one; release timers or expensive resources. */
    default void onHide() {
    }
}
