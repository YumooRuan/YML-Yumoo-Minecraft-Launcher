package dev.yml.core.game;

import java.util.Locale;

/** What kind of mod loader a version folder contains. */
public enum LoaderType {

    VANILLA("原版", "vanilla"),
    FABRIC("Fabric", "fabric"),
    QUILT("Quilt", "quilt"),
    FORGE("Forge", "forge"),
    NEOFORGE("NeoForge", "neoforge"),
    OPTIFINE("OptiFine", "optifine"),
    LITELOADER("LiteLoader", "liteloader"),
    UNKNOWN("未知", "unknown");

    private final String label;
    private final String id;

    LoaderType(String label, String id) {
        this.label = label;
        this.id = id;
    }

    public String label() {
        return label;
    }

    public String id() {
        return id;
    }

    public static LoaderType detect(VersionInfo version) {
        if (version == null) {
            return UNKNOWN;
        }
        for (Library library : version.libraries) {
            String name = library.name.toLowerCase(Locale.ROOT);
            if (name.startsWith("net.fabricmc:fabric-loader")) {
                return FABRIC;
            }
            if (name.startsWith("org.quiltmc:quilt-loader")) {
                return QUILT;
            }
            if (name.startsWith("net.neoforged:neoforge") || name.contains("neoforged:fancymodloader")) {
                return NEOFORGE;
            }
            if (name.startsWith("net.minecraftforge:forge")
                    || name.startsWith("net.minecraftforge:fmlloader")
                    || name.startsWith("cpw.mods:bootstraplauncher")) {
                return FORGE;
            }
            if (name.startsWith("optifine:optifine") || name.startsWith("optifine:OptiFine".toLowerCase(Locale.ROOT))) {
                return OPTIFINE;
            }
            if (name.startsWith("com.mumfrey:liteloader")) {
                return LITELOADER;
            }
        }
        String id = version.id == null ? "" : version.id.toLowerCase(Locale.ROOT);
        if (id.contains("neoforge")) {
            return NEOFORGE;
        }
        if (id.contains("forge")) {
            return FORGE;
        }
        if (id.contains("fabric")) {
            return FABRIC;
        }
        if (id.contains("quilt")) {
            return QUILT;
        }
        if (id.contains("optifine")) {
            return OPTIFINE;
        }
        return VANILLA;
    }
}
