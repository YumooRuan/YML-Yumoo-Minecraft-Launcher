package dev.yml.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** File helpers used across the launcher. */
public final class FileUtil {

    private FileUtil() {
    }

    public static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建目录 " + dir, e);
        }
    }

    public static void deleteRecursive(Path target) {
        if (!Files.exists(target)) {
            return;
        }
        try {
            Files.walkFileTree(target, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("无法删除 " + target, e);
        }
    }

    public static byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new RuntimeException("无法读取 " + file, e);
        }
    }

    public static String readString(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public static void writeString(Path file, String content) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("无法写入 " + file, e);
        }
    }

    public static void writeBytes(Path file, byte[] data) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, data);
        } catch (IOException e) {
            throw new RuntimeException("无法写入 " + file, e);
        }
    }

    public static void move(Path from, Path to) {
        try {
            if (to.getParent() != null) {
                Files.createDirectories(to.getParent());
            }
            try {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("无法移动 " + from + " -> " + to, e);
        }
    }

    public static void copy(Path from, Path to) {
        try {
            if (to.getParent() != null) {
                Files.createDirectories(to.getParent());
            }
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("无法复制 " + from + " -> " + to, e);
        }
    }

    public static String sha1(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[64 * 1024];
            int read;
            while ((read = in.read(buf)) > 0) {
                digest.update(buf, 0, read);
            }
            return hex(digest.digest());
        } catch (Exception e) {
            return null;
        }
    }

    public static String sha1(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return hex(digest.digest(data));
        } catch (Exception e) {
            return null;
        }
    }

    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1;
        }
    }

    public static List<Path> list(Path dir, String suffix) {
        List<Path> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return out;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> suffix == null || p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(suffix))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException ignored) {
        }
        return out;
    }

    public static List<Path> dirs(Path dir) {
        List<Path> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return out;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isDirectory).sorted().forEach(out::add);
        } catch (IOException ignored) {
        }
        return out;
    }

    /** Extracts a zip archive. Entry paths that are absolute or escape the target are refused. */
    public static void extractZip(Path zipFile, Path targetDir, Predicate<String> skip) {
        ensureDir(targetDir);
        try (ZipFile zip = new ZipFile(zipFile.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (skip != null && skip.test(name)) {
                    continue;
                }
                Path out = targetDir.resolve(name).normalize();
                if (!out.startsWith(targetDir.normalize())) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    if (out.getParent() != null) {
                        Files.createDirectories(out.getParent());
                    }
                    try (InputStream in = zip.getInputStream(entry);
                         OutputStream os = Files.newOutputStream(out)) {
                        in.transferTo(os);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("解压失败 " + zipFile, e);
        }
    }

    /** Reads a stream to a file, returning the byte count. */
    public static long writeStream(InputStream in, Path dest) throws IOException {
        if (dest.getParent() != null) {
            Files.createDirectories(dest.getParent());
        }
        long total = 0;
        byte[] buf = new byte[128 * 1024];
        try (OutputStream out = Files.newOutputStream(dest)) {
            int read;
            while ((read = in.read(buf)) >= 0) {
                out.write(buf, 0, read);
                total += read;
            }
        }
        return total;
    }

    public static String fileName(Path path) {
        return path == null || path.getFileName() == null ? "" : path.getFileName().toString();
    }

    public static String humanSize(long bytes) {
        if (bytes < 0) {
            return "未知";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }
}
