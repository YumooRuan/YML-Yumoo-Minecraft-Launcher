package dev.yml.core.net;

import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;
import dev.yml.core.util.Progress;
import dev.yml.core.util.TaskCancelledException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Parallel download engine with mirror fallback, retries and SHA-1 verification.
 */
public final class Downloader {

    public record Item(String label, List<String> urls, Path dest, String sha1, long size) {

        public static Item of(String label, String url, Path dest, String sha1, long size) {
            return new Item(label, List.of(url), dest, sha1, size);
        }

        public static Item of(String label, List<String> urls, Path dest, String sha1, long size) {
            return new Item(label, urls, dest, sha1, size);
        }
    }

    private final int threads;
    private final int retries;
    private final AtomicLong done = new AtomicLong();
    private final dev.yml.core.util.RateMeter rateMeter = new dev.yml.core.util.RateMeter();
    private volatile long total;

    public Downloader(int threads) {
        this(threads, 3);
    }

    public Downloader(int threads, int retries) {
        this.threads = Math.max(1, threads);
        this.retries = Math.max(1, retries);
    }

    /** Downloads all items, skipping ones already present and valid. Returns bytes transferred. */
    public long downloadAll(List<Item> items, Progress progress) {
        if (items.isEmpty()) {
            return 0;
        }
        List<Item> pending = new ArrayList<>();
        long expected = 0;
        for (Item item : items) {
            if (isPresent(item)) {
                if (item.size() > 0) {
                    done.addAndGet(item.size());
                    expected += item.size();
                }
                continue;
            }
            pending.add(item);
            expected += Math.max(0, item.size());
        }
        total = expected;
        if (pending.isEmpty()) {
            if (progress != null) {
                progress.update(done.get(), total, "文件已完整");
            }
            return 0;
        }
        long before = done.get();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, pending.size()), r -> {
            Thread t = new Thread(r, "yml-download");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> futures = new ArrayList<>(pending.size());
            for (Item item : pending) {
                futures.add(pool.submit(() -> {
                    downloadOne(item, progress);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (java.util.concurrent.ExecutionException e) {
            pool.shutdownNow();
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause == null ? e : cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
            throw new TaskCancelledException("下载已中断");
        } finally {
            pool.shutdownNow();
        }
        if (progress != null) {
            progress.update(done.get(), Math.max(total, done.get()), "下载完成");
        }
        return done.get() - before;
    }

    public void downloadOne(Item item, Progress progress) {
        if (isPresent(item)) {
            return;
        }
        IOException last = null;
        List<String> urls = item.urls().isEmpty() ? List.of() : item.urls();
        for (String url : urls) {
            for (int attempt = 1; attempt <= retries; attempt++) {
                checkCancelled(progress);
                Path part = item.dest().resolveSibling(item.dest().getFileName() + ".part");
                try {
                    FileUtil.ensureDir(item.dest().getParent());
                    Files.deleteIfExists(part);
                    Log.debug("下载 %s <- %s", item.label(), url);
                    Http.download(url, part, item.size(), (d, t) -> {
                        if (progress != null) {
                            long current = done.get() + d;
                            progress.update(current, Math.max(total, d), item.label());
                            long bps = rateMeter.sample(current);
                            if (bps > 0) {
                                progress.speed(bps);
                            }
                        }
                    });
                    if (item.sha1() != null && !item.sha1().isEmpty()) {
                        String actual = FileUtil.sha1(part);
                        if (!item.sha1().equalsIgnoreCase(actual)) {
                            Files.deleteIfExists(part);
                            throw new IOException("SHA-1 校验失败: " + item.label() + "（期望 " + item.sha1() + "，实际 " + actual + "）");
                        }
                    }
                    FileUtil.move(part, item.dest());
                    done.addAndGet(item.size() > 0 ? item.size() : FileUtil.size(item.dest()));
                    if (progress != null) {
                        progress.update(done.get(), Math.max(total, done.get()), item.label());
                    }
                    return;
                } catch (Http.HttpStatusException e) {
                    last = e;
                    Log.debug("HTTP %d: %s", e.status(), url);
                    if (e.status() == 404 || e.status() == 403) {
                        break; // try the next mirror
                    }
                } catch (IOException e) {
                    last = e;
                    Log.debug("下载失败(%d/%d) %s: %s", attempt, retries, url, e.getMessage());
                }
                sleepQuietly(300L * attempt);
            }
        }
        throw new RuntimeException("下载失败: " + item.label() + (last != null ? " (" + last.getMessage() + ")" : ""),
                last);
    }

    private void checkCancelled(Progress progress) {
        if (progress != null && progress.cancelled()) {
            throw new TaskCancelledException();
        }
    }

    public static boolean isPresent(Item item) {
        if (!Files.isRegularFile(item.dest())) {
            return false;
        }
        if (item.sha1() != null && !item.sha1().isEmpty()) {
            return item.sha1().equalsIgnoreCase(FileUtil.sha1(item.dest()));
        }
        return item.size() <= 0 || FileUtil.size(item.dest()) == item.size();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskCancelledException();
        }
    }
}
