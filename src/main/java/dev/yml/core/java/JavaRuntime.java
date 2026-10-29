package dev.yml.core.java;

import dev.yml.core.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** A discovered or downloaded Java runtime. */
public final class JavaRuntime {

    public final Path executable;
    public final Path home;
    public final String version;
    public final int major;
    public final String vendor;
    public final String arch;
    public final boolean javac;
    public final String source;

    public JavaRuntime(Path executable, Path home, String version, int major, String vendor, String arch,
                       boolean javac, String source) {
        this.executable = executable;
        this.home = home;
        this.version = version;
        this.major = major;
        this.vendor = vendor;
        this.arch = arch;
        this.javac = javac;
        this.source = source;
    }

    public String label() {
        return "Java " + major + (isLts(major) ? " (LTS)" : "");
    }

    public String detail() {
        return vendor + " " + version + " · " + arch;
    }

    @Override
    public String toString() {
        return label() + " " + detail();
    }

    public static boolean isLts(int major) {
        return major == 8 || major == 11 || major == 17 || major == 21 || major == 25;
    }

    public static int parseMajor(String version) {
        if (version == null || version.isBlank()) {
            return 0;
        }
        String v = version.trim();
        try {
            if (v.startsWith("1.")) {
                int end = v.indexOf('.', 2);
                return Integer.parseInt(end < 0 ? v.substring(2) : v.substring(2, end));
            }
            int end = 0;
            while (end < v.length() && Character.isDigit(v.charAt(end))) {
                end++;
            }
            if (end == 0) {
                return 0;
            }
            return Integer.parseInt(v.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Runs {@code java -XshowSettings:properties -version} and parses the properties. */
    public static JavaRuntime probe(Path javaExe, String source) {
        if (javaExe == null || !Files.isRegularFile(javaExe)) {
            return null;
        }
        Map<String, String> props = new LinkedHashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(javaExe.toString(), "-XshowSettings:properties", "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    int eq = trimmed.indexOf(" = ");
                    if (eq > 0) {
                        String key = trimmed.substring(0, eq).trim();
                        String value = trimmed.substring(eq + 3).trim();
                        props.putIfAbsent(key, value);
                    }
                }
            }
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
        } catch (IOException e) {
            Log.debug("探测 Java 失败 %s: %s", javaExe, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        String version = props.getOrDefault("java.version", "");
        if (version.isBlank()) {
            return null;
        }
        int major = parseMajor(version);
        if (major <= 0) {
            return null;
        }
        String javaHome = props.getOrDefault("java.home", "");
        Path home = javaHome.isBlank() ? javaExe.getParent().getParent() : Path.of(javaHome);
        String vendor = vendorOf(props.getOrDefault("java.vendor", ""), props.getOrDefault("java.vm.name", ""),
                javaExe.toString());
        String arch = normalizeArch(props.getOrDefault("os.arch", System.getProperty("os.arch", "")));
        boolean javac = Files.isRegularFile(home.resolve("bin").resolve(isWindowsExe(javaExe) ? "javac.exe" : "javac"));
        return new JavaRuntime(javaExe.toAbsolutePath().normalize(), home.toAbsolutePath().normalize(),
                version, major, vendor, arch, javac, source);
    }

    private static boolean isWindowsExe(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".exe");
    }

    public static String normalizeArch(String raw) {
        String arch = raw == null ? "" : raw.toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "arm64";
        }
        if (arch.contains("64")) {
            return "x64";
        }
        return "x86";
    }

    private static String vendorOf(String javaVendor, String vmName, String path) {
        String all = (javaVendor + " " + vmName + " " + path).toLowerCase();
        if (all.contains("temurin") || all.contains("adoptium") || all.contains("eclipse")) {
            return "Adoptium Temurin";
        }
        if (all.contains("microsoft")) {
            return "Microsoft";
        }
        if (all.contains("zulu") || all.contains("azul")) {
            return "Azul Zulu";
        }
        if (all.contains("corretto") || all.contains("amazon")) {
            return "Amazon Corretto";
        }
        if (all.contains("bellsoft") || all.contains("liberica")) {
            return "BellSoft Liberica";
        }
        if (all.contains("semeru") || all.contains("ibm")) {
            return "IBM Semeru";
        }
        if (all.contains("graal")) {
            return "GraalVM";
        }
        if (all.contains("oracle") || all.contains("hotspot")) {
            return "Oracle";
        }
        if (all.contains("openjdk") || all.contains("open jdk")) {
            return "OpenJDK";
        }
        return javaVendor.isBlank() ? "Java" : javaVendor;
    }
}
