package dev.yml.core.util;

/** Thrown when a long-running task is cancelled by the user. */
public class TaskCancelledException extends RuntimeException {

    public TaskCancelledException() {
        super("任务已取消");
    }

    public TaskCancelledException(String message) {
        super(message);
    }
}
