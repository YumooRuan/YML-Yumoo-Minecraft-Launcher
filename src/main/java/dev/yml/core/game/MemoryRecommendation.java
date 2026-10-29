package dev.yml.core.game;

import dev.yml.core.config.InstanceSettings;

/** Recommended heap sizes per loader, used when an instance is set to "auto". */
public final class MemoryRecommendation {

    public static final int MIN_MB = 512;
    public static final int MAX_MB = 32768;
    public static final int STEP_MB = 256;

    private MemoryRecommendation() {
    }

    public static int recommend(LocalVersions.Installed installed) {
        int base;
        if (installed == null) {
            base = 8192;
        } else {
            base = switch (installed.loader()) {
                case FABRIC, FORGE, NEOFORGE, QUILT -> 8192;
                case OPTIFINE -> 8192;
                default -> 8192;
            };
        }
        // Never recommend more than half of physical RAM; keep at least 1 GB for the system.
        long total = dev.yml.core.util.OsInfo.totalMemoryMb();
        if (total > 0) {
            int cap = (int) Math.max(1024, total / 2);
            base = Math.min(base, cap);
        }
        return snap(base);
    }

    /** Effective heap: explicit instance value, otherwise the per-loader recommendation. */
    public static int effective(LocalVersions.Installed installed, InstanceSettings settings) {
        if (settings != null && settings.memoryMb > 0) {
            return clamp(settings.memoryMb);
        }
        return recommend(installed);
    }

    public static int clamp(int memoryMb) {
        return Math.max(MIN_MB, Math.min(MAX_MB, memoryMb));
    }

    public static int snap(int memoryMb) {
        int snapped = Math.round(memoryMb / (float) STEP_MB) * STEP_MB;
        return clamp(snapped);
    }
}
