package dev.yml.core.util;

/** Progress callback used by downloads and installers. */
@FunctionalInterface
public interface Progress {

    Progress NONE = (done, total, note) -> {
    };

    void update(long done, long total, String note);

    /** Optional live download rate, in bytes per second. */
    default void speed(long bytesPerSecond) {
    }

    default boolean cancelled() {
        return false;
    }

    default void note(String note) {
        update(0, 0, note);
    }
}
