package dev.yml.core.util;

import java.util.Locale;

/** Host operating system facts that Minecraft's rule system cares about. */
public final class OsInfo {

    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final String OS_ARCH = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

    private OsInfo() {
    }

    /** Mojang's naming: windows / osx / linux. */
    public static String name() {
        if (OS_NAME.contains("win")) {
            return "windows";
        }
        if (OS_NAME.contains("mac") || OS_NAME.contains("darwin")) {
            return "osx";
        }
        return "linux";
    }

    /** Mojang's naming: x86 / x86_64 / arm64. */
    public static String arch() {
        if (OS_ARCH.contains("aarch64") || OS_ARCH.contains("arm64")) {
            return "arm64";
        }
        if (OS_ARCH.contains("64")) {
            return "x86_64";
        }
        return "x86";
    }

    /** 32 or 64, used by old native classifiers such as {@code natives-windows-${arch}}. */
    public static int bits() {
        return "x86".equals(arch()) ? 32 : 64;
    }

    public static String version() {
        return System.getProperty("os.version", "");
    }

    public static boolean isWindows() {
        return "windows".equals(name());
    }

    public static boolean isMac() {
        return "osx".equals(name());
    }

    /** Adoptium / Mojang platform keys, e.g. {@code windows-x64}. */
    public static String platformKey() {
        return name() + "-" + ("x86".equals(arch()) ? "x86" : arch().equals("arm64") ? "aarch64" : "x64");
    }

    public static String archDisplay() {
        return switch (arch()) {
            case "x86_64" -> "x86_64";
            case "arm64" -> "ARM64";
            default -> "x86";
        };
    }

    /** Physical memory in MB, or 0 when the JVM cannot report it. */
    public static long totalMemoryMb() {
        try {
            Object bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            java.lang.reflect.Method method = bean.getClass().getMethod("getTotalMemorySize");
            return ((Number) method.invoke(bean)).longValue() / (1024L * 1024L);
        } catch (Exception e) {
            return 0;
        }
    }
}
