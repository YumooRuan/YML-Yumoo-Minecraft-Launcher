package dev.yml.core.auth;

import dev.yml.core.json.Json;
import dev.yml.core.net.Http;
import dev.yml.core.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Yggdrasil / authlib-injector compatible authentication (LittleSkin, Blessing Skin servers, ...).
 */
public final class ExternalAuth {

    public static final String LITTLESKIN = "https://littleskin.cn/api/yggdrasil";

    public record Profile(String id, String name) {
    }

    public record Result(String accessToken, String clientToken, String uuid, String name,
                         List<Profile> profiles) {
    }

    private ExternalAuth() {
    }

    public static Result authenticate(String apiRoot, String username, String password) throws IOException {
        String root = normalize(apiRoot);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("clientToken", java.util.UUID.randomUUID().toString().replace("-", ""));
        Map<String, Object> agent = new LinkedHashMap<>();
        agent.put("name", "Minecraft");
        agent.put("version", 1);
        body.put("agent", agent);
        body.put("requestUser", true);

        String text;
        try {
            text = Http.postJson(root + "/authserver/authenticate", Json.write(body, false));
        } catch (Http.ApiException e) {
            String message = parseError(e.body());
            if (e.status() == 403) {
                throw new IOException(message == null ? "用户名或密码错误，或服务器拒绝登录" : message, e);
            }
            throw new IOException("外置登录失败 (HTTP " + e.status() + ")"
                    + (message == null ? "" : ": " + message), e);
        }
        Map<String, Object> json = Json.obj(Json.parse(text));
        String accessToken = Json.str(json.get("accessToken"));
        String clientToken = Json.str(json.get("clientToken"));
        if (accessToken == null || accessToken.isBlank()) {
            throw new IOException("外置登录返回的数据不完整（缺少 accessToken）");
        }
        List<Profile> profiles = new ArrayList<>();
        for (Object entry : Json.arrOrEmpty(json.get("availableProfiles"))) {
            Map<String, Object> profile = Json.objOrEmpty(entry);
            String id = Json.str(profile.get("id"));
            String name = Json.str(profile.get("name"));
            if (id != null && name != null) {
                profiles.add(new Profile(id.replace("-", ""), name));
            }
        }
        Map<String, Object> selected = Json.objOrEmpty(json.get("selectedProfile"));
        String uuid = Json.str(selected.get("id"));
        String name = Json.str(selected.get("name"));
        if ((uuid == null || name == null) && !profiles.isEmpty()) {
            uuid = profiles.get(0).id();
            name = profiles.get(0).name();
        }
        if (uuid == null || name == null) {
            throw new IOException("该账户还没有创建 Minecraft 角色，请先在 LittleSkin 网站创建角色");
        }
        Log.info("外置登录成功: %s @ %s", name, root);
        return new Result(accessToken, clientToken, uuid.replace("-", ""), name, profiles);
    }

    private static String normalize(String apiRoot) {
        String root = apiRoot == null || apiRoot.isBlank() ? LITTLESKIN : apiRoot.trim();
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        return root;
    }

    private static String parseError(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> json = Json.obj(Json.parse(body));
            String message = Json.str(json.get("errorMessage"));
            if (message == null) {
                message = Json.str(json.get("error"));
            }
            return message;
        } catch (Exception e) {
            return null;
        }
    }

    public static String displayApiRoot(String apiRoot) {
        String root = normalize(apiRoot);
        return root.replace("https://", "").replace("http://", "");
    }
}
