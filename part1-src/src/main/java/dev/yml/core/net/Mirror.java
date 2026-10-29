package dev.yml.core.net;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Download sources and URL translation.
 *
 * <p>Each source knows how to translate the official Mojang/Fabric/Forge URLs to its own
 * mirror. {@link Source#AUTO} keeps the official URL first and appends the domestic mirrors
 * as fallbacks, so a broken mirror never blocks an install that could otherwise succeed.</p>
 */
public final class Mirror {

    public static final String BMCLAPI = "https://bmclapi2.bangbang93.com";
    public static final String ALIYUN = "https://maven.aliyun.com/repository/public";
    public static final String TUNA = "https://mirrors.tuna.tsinghua.edu.cn";

    public enum Source {
        AUTO("AUTO", "自动选择", "官方优先，失败时自动切换国内镜像"),
        OFFICIAL("OFFICIAL", "Mojang 官方源", "直连官方，海外网络最佳"),
        BMCLAPI("BMCLAPI", "BMCLAPI 镜像（国内）", "国内加速，覆盖版本、资源与依赖"),
        ALIYUN("ALIYUN", "阿里云 Maven（国内）", "依赖库走阿里云公共仓库"),
        TUNA("TUNA", "清华 TUNA（国内）", "教育网加速，适合 Java 运行时下载");

        private final String id;
        private final String label;
        private final String description;

        Source(String id, String label, String description) {
            this.id = id;
            this.label = label;
            this.description = description;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }

        public static Source of(String id) {
            if (id != null) {
                for (Source source : values()) {
                    if (source.id.equalsIgnoreCase(id) || source.name().equalsIgnoreCase(id)) {
                        return source;
                    }
                }
            }
            return AUTO;
        }
    }

    private Mirror() {
    }

    /** Returns the candidate URLs for one logical file, in the order they should be tried. */
    public static List<String> candidates(String officialUrl, Source source) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        switch (source == null ? Source.AUTO : source) {
            case OFFICIAL -> urls.add(officialUrl);
            case BMCLAPI -> {
                add(urls, bmclapi(officialUrl));
                urls.add(officialUrl);
            }
            case ALIYUN -> {
                add(urls, aliyun(officialUrl));
                urls.add(officialUrl);
            }
            case TUNA -> {
                add(urls, tuna(officialUrl));
                urls.add(officialUrl);
            }
            case AUTO -> {
                urls.add(officialUrl);
                add(urls, bmclapi(officialUrl));
                add(urls, aliyun(officialUrl));
                add(urls, tuna(officialUrl));
            }
        }
        if (urls.isEmpty()) {
            urls.add(officialUrl);
        }
        return new ArrayList<>(urls);
    }

    private static void add(LinkedHashSet<String> urls, String url) {
        if (url != null && !url.isBlank()) {
            urls.add(url);
        }
    }

    /** Maps an official URL onto BMCLAPI, or returns {@code null} when there is no known route. */
    public static String bmclapi(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json")) {
            return BMCLAPI + "/mc/game/version_manifest_v2.json";
        }
        if (url.startsWith("https://libraries.minecraft.net/")) {
            return BMCLAPI + "/maven/" + url.substring("https://libraries.minecraft.net/".length());
        }
        if (url.startsWith("https://resources.download.minecraft.net/")) {
            return BMCLAPI + "/assets/" + url.substring("https://resources.download.minecraft.net/".length());
        }
        if (url.startsWith("https://maven.fabricmc.net/")) {
            return BMCLAPI + "/maven/" + url.substring("https://maven.fabricmc.net/".length());
        }
        if (url.startsWith("https://meta.fabricmc.net/")) {
            return BMCLAPI + "/fabric-meta/" + url.substring("https://meta.fabricmc.net/".length());
        }
        if (url.startsWith("https://maven.minecraftforge.net/")) {
            return BMCLAPI + "/maven/" + url.substring("https://maven.minecraftforge.net/".length());
        }
        if (url.startsWith("https://maven.neoforged.net/releases/")) {
            return BMCLAPI + "/maven/" + url.substring("https://maven.neoforged.net/releases/".length());
        }
        return null;
    }

    /** Maps a Mojang library URL onto Aliyun's public Maven repository. */
    public static String aliyun(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("https://libraries.minecraft.net/")) {
            return ALIYUN + "/" + url.substring("https://libraries.minecraft.net/".length());
        }
        return null;
    }

    /** Tsinghua mirrors Adoptium and a few Mojang assets; only explicit routes are translated. */
    public static String tuna(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("https://libraries.minecraft.net/")) {
            return TUNA + "/minecraft/libraries/" + url.substring("https://libraries.minecraft.net/".length());
        }
        return null;
    }

    public static boolean isOfficial(String url) {
        String host = Http.host(url);
        return host.endsWith("mojang.com") || host.endsWith("minecraft.net") || host.endsWith("fabricmc.net")
                || host.endsWith("minecraftforge.net") || host.endsWith("neoforged.net");
    }

    public static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
