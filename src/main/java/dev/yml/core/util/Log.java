package dev.yml.core.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Minimal application logger with file output and in-process listeners. */
public final class Log {

    public record Entry(LocalDateTime time, String level, String message) {
        public String format() {
            return time.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + " [" + level + "] " + message;
        }
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final List<Consumer<Entry>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Object FILE_LOCK = new Object();
    private static volatile Path file;
    private static volatile boolean debug;

    private Log() {
    }

    public static void init(Path logFile, boolean debugEnabled) {
        file = logFile;
        debug = debugEnabled;
        try {
            if (logFile.getParent() != null) {
                Files.createDirectories(logFile.getParent());
            }
            if (Files.exists(logFile) && Files.size(logFile) > 4 * 1024 * 1024) {
                Files.move(logFile, logFile.resolveSibling(logFile.getFileName() + ".old"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }

    public static void addListener(Consumer<Entry> listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Consumer<Entry> listener) {
        LISTENERS.remove(listener);
    }

    public static void info(String message, Object... args) {
        log("INFO", message, args, null);
    }

    public static void warn(String message, Object... args) {
        log("WARN", message, args, null);
    }

    public static void error(String message, Object... args) {
        log("ERROR", message, args, null);
    }

    public static void error(Throwable t, String message, Object... args) {
        log("ERROR", message, args, t);
    }

    public static void debug(String message, Object... args) {
        if (debug) {
            log("DEBUG", message, args, null);
        }
    }

    private static void log(String level, String message, Object[] args, Throwable t) {
        String text;
        try {
            text = args == null || args.length == 0 ? message : String.format(message, args);
        } catch (RuntimeException e) {
            text = message + " " + java.util.Arrays.toString(args);
        }
        if (t != null) {
            text = text + " | " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        Entry entry = new Entry(LocalDateTime.now(), level, text);
        for (Consumer<Entry> l : LISTENERS) {
            try {
                l.accept(entry);
            } catch (RuntimeException ignored) {
            }
        }
        if (level.equals("ERROR")) {
            System.err.println(entry.format());
        } else if (level.equals("DEBUG")) {
            System.out.println(entry.format());
        }
        Path f = file;
        if (f != null) {
            synchronized (FILE_LOCK) {
                try (BufferedWriter w = Files.newBufferedWriter(f, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    w.write(entry.time().format(TIME));
                    w.write(" [");
                    w.write(level);
                    w.write("] ");
                    w.write(text);
                    if (t != null) {
                        w.write(System.lineSeparator());
                        var sw = new java.io.StringWriter();
                        t.printStackTrace(new java.io.PrintWriter(sw));
                        w.write(sw.toString());
                    }
                    w.newLine();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
