package dev.yml.ui.page.download;

import javafx.scene.Node;

/** One tab inside the download center. */
public interface DownloadTab {

    Node view();

    default void onShow() {
    }

    /** Re-fetches whatever remote data the tab depends on (e.g. after switching mirrors). */
    default void refresh() {
        onShow();
    }
}
