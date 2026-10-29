package dev.yml.core.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** The currently selected player profile. */
public final class Account {

    public enum Type {
        OFFLINE("离线账户"),
        MICROSOFT("微软账户"),
        EXTERNAL("外置登录");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public String name;
    public Type type = Type.OFFLINE;
    public String uuid;
    public String accessToken = "0";
    public String refreshToken;
    /** authlib-injector API root for {@link Type#EXTERNAL} accounts. */
    public String apiRoot;
    /** "STEVE" / "ALEX" / "PREMIUM" / "LOCAL". */
    public String skinMode = "STEVE";
    /** Local PNG path for {@link #skinMode} = LOCAL. */
    public String skinPath;
    /** Token expiry (epoch millis) for Microsoft accounts; 0 = unknown. */
    public long expiresAt;

    public Account() {
    }

    public Account(String name, Type type) {
        this.name = name;
        this.type = type;
        this.uuid = offlineUuid(name);
    }

    public static Account offline(String name) {
        return new Account(name, Type.OFFLINE);
    }

    /** Same UUID derivation as vanilla offline mode / other launchers. */
    public static String offlineUuid(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return uuid.toString().replace("-", "");
    }

    public String displayUuid() {
        if (uuid == null || uuid.length() != 32) {
            return uuid == null ? "" : uuid;
        }
        return uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16) + "-"
                + uuid.substring(16, 20) + "-" + uuid.substring(20);
    }

    public String initial() {
        return name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
    }

    @Override
    public String toString() {
        return name + " (" + type.label() + ")";
    }
}
