package dev.yml.core.auth;

import dev.yml.core.json.Json;
import dev.yml.core.net.Downloader;
import dev.yml.core.net.Http;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;
import dev.yml.core.util.Progress;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Downloads and locates authlib-injector, required for external (Yggdrasil) logins. */
public final class AuthlibInjector {

    public static final String LATEST_JSON = "https://authlib-injector.yushi.moe/artifact/latest.json";

    private AuthlibInjector() {
    }

    public static Path ensure(Path dataRoot, Progress progress) throws IOException {
        Path runtime = dataRoot.resolve("runtime");
        FileUtil.ensureDir(runtime);
        Path cached;
        try (var stream = Files.list(runtime)) {
            cached = stream.filter(p -> p.getFileName().toString().startsWith("authlib-injector-")
                            && p.getFileName().toString().endsWith(".jar"))
                    .max((a, b) -> Long.compare(a.toFile().lastModified(), b.toFile().lastModified()))
                    .orElse(null);
        }
        if (cached != null && Files.size(cached) > 100_000) {
            return cached;
        }
        if (progress != null) {
            progress.note("下载 authlib-injector");
        }
        try {
            String text = Http.text(LATEST_JSON, 512 * 1024);
            Map<String, Object> json = Json.obj(Json.parse(text));
            String version = Json.str(json.get("version"), "1.2.8");
            String url = Json.str(json.get("download_url"));
            if (url == null) {
                throw new IOException("authlib-injector 下载地址缺失");
            }
            Path target = runtime.resolve("authlib-injector-" + version + ".jar");
            new Downloader(2).downloadAll(List.of(Downloader.Item.of("authlib-injector", url, target, null, 0)),
                    progress);
            Log.info("authlib-injector 就绪: %s", target);
            return target;
        } catch (Exception e) {
            throw new IOException("无法获取 authlib-injector: " + e.getMessage(), e);
        }
    }

    public static String javaAgentArgument(Path jar, String apiRoot) {
        return "-javaagent:" + jar.toAbsolutePath() + "=" + apiRoot;
    }
}
