package dev.yml.core.auth;

import dev.yml.core.json.Json;
import dev.yml.core.net.Http;
import dev.yml.core.util.Log;
import dev.yml.core.util.Progress;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Microsoft OAuth device-code login and the Xbox Live / Minecraft authentication chain. */
public final class MicrosoftAuth {

    private static final String DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String SCOPE = "XboxLive.signin offline_access";

    public record DeviceCode(String deviceCode, String userCode, String verificationUri, int interval,
                             int expiresIn) {
    }

    public record MinecraftAccount(String accessToken, String uuid, String name, long expiresAt,
                                   String refreshToken) {
    }

    private MicrosoftAuth() {
    }

    public static DeviceCode start(String clientId) throws IOException {
        if (clientId == null || clientId.isBlank()) {
            throw new IOException("尚未填写 Microsoft 应用 Client ID");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("scope", SCOPE);
        Map<String, Object> json = post(DEVICE_CODE_URL, form);
        return new DeviceCode(
                Json.str(json.get("device_code"), ""),
                Json.str(json.get("user_code"), ""),
                Json.str(json.get("verification_uri"), "https://microsoft.com/link"),
                Json.integer(json.get("interval"), 5),
                Json.integer(json.get("expires_in"), 900));
    }

    /** Runs the full flow, invoking {@code onCode} once the user code is available. */
    public static MinecraftAccount login(String clientId, Consumer<DeviceCode> onCode, Progress progress,
                                         BooleanSupplier cancelled) throws IOException {
        DeviceCode code = start(clientId);
        onCode.accept(code);
        if (progress != null) {
            progress.note("请在浏览器打开 " + code.verificationUri() + " 并输入 " + code.userCode());
        }
        long deadline = System.currentTimeMillis() + code.expiresIn() * 1000L;
        int interval = Math.max(3, code.interval());
        String accessToken = null;
        String refreshToken = null;
        while (System.currentTimeMillis() < deadline) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                throw new IOException("登录已取消");
            }
            sleep(interval * 1000L);
            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_id", clientId);
            form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            form.put("device_code", code.deviceCode());
            try {
                Map<String, Object> token = post(TOKEN_URL, form);
                accessToken = Json.str(token.get("access_token"));
                refreshToken = Json.str(token.get("refresh_token"));
                break;
            } catch (Http.ApiException e) {
                String error = Json.str(Json.objOrEmpty(Json.parse(e.body())).get("error"), "");
                if ("authorization_pending".equals(error)) {
                    continue;
                }
                if ("slow_down".equals(error)) {
                    interval += 5;
                    continue;
                }
                if ("expired_token".equals(error) || "code_expired".equals(error)) {
                    throw new IOException("设备码已过期，请重新登录");
                }
                throw new IOException("微软登录失败: " + error, e);
            }
        }
        if (accessToken == null) {
            throw new IOException("微软登录超时，请重试");
        }
        return finish(accessToken, refreshToken);
    }

    public static MinecraftAccount refresh(String clientId, String refreshToken) throws IOException {
        if (clientId == null || clientId.isBlank() || refreshToken == null || refreshToken.isBlank()) {
            throw new IOException("缺少 Microsoft Client ID 或 refresh token");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("scope", SCOPE);
        Map<String, Object> token = post(TOKEN_URL, form);
        String access = Json.str(token.get("access_token"));
        String refresh = Json.str(token.get("refresh_token"), refreshToken);
        return finish(access, refresh);
    }

    private static MinecraftAccount finish(String msToken, String refreshToken) throws IOException {
        // 1. Xbox Live user token
        Map<String, Object> xblProps = new LinkedHashMap<>();
        xblProps.put("AuthMethod", "RPS");
        xblProps.put("SiteName", "user.auth.xboxlive.com");
        xblProps.put("RpsTicket", "d=" + msToken);
        Map<String, Object> xblBody = new LinkedHashMap<>();
        xblBody.put("Properties", xblProps);
        xblBody.put("RelyingParty", "http://auth.xboxlive.com");
        xblBody.put("TokenType", "JWT");
        Map<String, Object> xbl = postJson("https://user.auth.xboxlive.com/user/authenticate", xblBody);
        String xblToken = Json.str(xbl.get("Token"), "");
        String uhs = Json.getStr("", xbl, "DisplayClaims", "xui");
        String userHash = "";
        for (Object entry : Json.arrOrEmpty(Json.get(xbl, "DisplayClaims", "xui"))) {
            userHash = Json.str(Json.objOrEmpty(entry).get("uhs"), "");
            break;
        }
        if (xblToken.isBlank() || userHash.isBlank()) {
            throw new IOException("Xbox Live 认证失败");
        }

        // 2. XSTS token
        Map<String, Object> xstsProps = new LinkedHashMap<>();
        xstsProps.put("SandboxId", "RETAIL");
        xstsProps.put("UserTokens", java.util.List.of(xblToken));
        Map<String, Object> xstsBody = new LinkedHashMap<>();
        xstsBody.put("Properties", xstsProps);
        xstsBody.put("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.put("TokenType", "JWT");
        Map<String, Object> xsts;
        try {
            xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody);
        } catch (Http.ApiException e) {
            throw new IOException("XSTS 认证失败，可能没有 Xbox 账户或未满 18 岁（Xbox 家庭设置限制）", e);
        }
        String xstsToken = Json.str(xsts.get("Token"), "");

        // 3. Minecraft access token
        Map<String, Object> mcBody = new LinkedHashMap<>();
        mcBody.put("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        Map<String, Object> mc = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcBody);
        String mcToken = Json.str(mc.get("access_token"), "");
        long expiresAt = System.currentTimeMillis() + Json.lng(mc.get("expires_in"), 86400) * 1000L;
        if (mcToken.isBlank()) {
            throw new IOException("Minecraft 服务认证失败");
        }

        // 4. Profile
        Map<String, Object> profile = getJson("https://api.minecraftservices.com/minecraft/profile", mcToken);
        String uuid = Json.str(profile.get("id"), "").replace("-", "");
        String name = Json.str(profile.get("name"), "");
        if (uuid.isBlank() || name.isBlank()) {
            throw new IOException("该微软账户没有 Minecraft: Java Edition 的拥有记录");
        }
        Log.info("微软登录成功: %s", name);
        return new MinecraftAccount(mcToken, uuid, name, expiresAt, refreshToken);
    }

    private static Map<String, Object> post(String url, Map<String, String> form) throws IOException {
        return Json.obj(Json.parse(Http.postForm(url, form)));
    }

    private static Map<String, Object> postJson(String url, Map<String, Object> body) throws IOException {
        return Json.obj(Json.parse(Http.postJson(url, Json.write(body, false))));
    }

    private static Map<String, Object> getJson(String url, String bearer) throws IOException {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("User-Agent", Http.USER_AGENT)
                .header("Authorization", "Bearer " + bearer)
                .GET().build();
        try {
            var response = Http.client().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return Json.obj(Json.parse(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断", e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("登录已中断");
        }
    }
}
