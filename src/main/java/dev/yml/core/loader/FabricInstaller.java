package dev.yml.core.loader;

import dev.yml.core.game.GameLauncher;
import dev.yml.core.game.VersionInfo;
import dev.yml.core.json.Json;
import dev.yml.core.net.Http;
import dev.yml.core.net.Mirror;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Log;
import dev.yml.core.util.Progress;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Installs Fabric Loader by fetching its generated version profile. */
public final class FabricInstaller {

    public static final String META = "https://meta.fabricmc.net";

    public record LoaderVersion(String version, boolean stable) {
        @Override
        public String toString() {
            return version + (stable ? "" : " (测试版)");
        }
    }

    private FabricInstaller() {
    }

    public static List<LoaderVersion> loaders(String mcVersion, Mirror.Source source) {
        String url = META + "/v2/versions/loader/" + mcVersion;
        for (String candidate : Mirror.candidates(url, source)) {
            try {
                String text = Http.text(candidate, 4 * 1024 * 1024);
                List<LoaderVersion> out = new ArrayList<>();
                for (Object entry : Json.arrOrEmpty(Json.parse(text))) {
                    Map<String, Object> map = Json.objOrEmpty(entry);
                    Object loader = map.get("loader");
                    if (loader instanceof Map<?, ?> l) {
                        Map<String, Object> lm = Json.obj(l);
                        String version = Json.str(lm.get("version"));
                        if (version != null) {
                            out.add(new LoaderVersion(version, Json.bool(lm.get("stable"), false)));
                        }
                    }
                }
                if (!out.isEmpty()) {
                    return out;
                }
            } catch (Exception e) {
                Log.warn("获取 Fabric 加载器列表失败 %s: %s", candidate, e.getMessage());
            }
        }
        return List.of();
    }

    /** Installs Fabric and returns the created version id. */
    public static String install(String mcVersion, String loaderVersion, Path mcRoot, Mirror.Source source,
                                 int threads, Progress progress) {
        String url = META + "/v2/versions/loader/" + mcVersion + "/" + loaderVersion + "/profile/json";
        String text = null;
        Exception last = null;
        for (String candidate : Mirror.candidates(url, source)) {
            try {
                text = Http.text(candidate, 8 * 1024 * 1024);
                break;
            } catch (Exception e) {
                last = e;
                Log.warn("Fabric 配置下载失败 %s: %s", candidate, e.getMessage());
            }
        }
        if (text == null) {
            throw new RuntimeException("无法获取 Fabric 安装配置" + (last == null ? "" : ": " + last.getMessage()));
        }
        Map<String, Object> profile = Json.obj(Json.parse(text));
        String id = Json.str(profile.get("id"), "fabric-loader-" + loaderVersion + "-" + mcVersion);
        profile.put("id", id);
        profile.putIfAbsent("type", "release");
        Path dir = mcRoot.resolve("versions").resolve(id);
        FileUtil.ensureDir(dir);
        Json.write(dir.resolve(id + ".json"), profile);
        if (progress != null) {
            progress.note("下载 Fabric 依赖");
        }
        VersionInfo info = VersionInfo.load(dir.resolve(id + ".json"), mcRoot.resolve("versions"));
        GameLauncher.ensureFiles(info, mcRoot, dir, source, threads, progress);
        return id;
    }
}
