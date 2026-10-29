package dev.yml.core.util;

import java.util.Locale;

/** Smoothed bytes-per-second meter used by download progress UIs. */
public final class RateMeter {

    private long lastBytes = -1;
    private long lastNanos;
    private double smoothed;

    /** Feed the cumulative byte count; returns the smoothed rate in bytes/second. */
    public synchronized long sample(long bytes) {
        long now = System.nanoTime();
        if (lastBytes < 0) {
            lastBytes = bytes;
            lastNanos = now;
            return 0;
        }
        long elapsed = now - lastNanos;
        if (elapsed < 250_000_000L) {
            return Math.max(0, (long) smoothed);
        }
        double instant = (bytes - lastBytes) * 1_000_000_000.0 / elapsed;
        smoothed = smoothed <= 0 ? instant : smoothed * 0.65 + instant * 0.35;
        lastBytes = bytes;
        lastNanos = now;
        return Math.max(0, (long) smoothed);
    }

    public static String format(long bytesPerSecond) {
        if (bytesPerSecond <= 0) {
            return "";
        }
        double kb = bytesPerSecond / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.0f KB/s", kb);
        }
        return String.format(Locale.ROOT, "%.2f MB/s", kb / 1024.0);
    }
}
