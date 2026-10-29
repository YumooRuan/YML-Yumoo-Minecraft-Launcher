package dev.yml.core.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Consumer;

/** Thin wrapper around {@link java.net.http.HttpClient}. */
public final class Http {

    public static final String USER_AGENT = "YML/1.0.0 (Windows 11; Java)";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private Http() {
    }

    public static HttpClient client() {
        return CLIENT;
    }

    private static HttpRequest.Builder base(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*");
    }

    public static String text(String url, int maxBytes) throws IOException {
        HttpRequest request = base(url).GET().build();
        HttpResponse<byte[]> response = send(request);
        byte[] body = response.body();
        if (body.length > maxBytes) {
            throw new IOException("响应过大: " + body.length + " 字节");
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    public static String postJson(String url, String json) throws IOException {
        HttpRequest request = base(url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return sendText(request);
    }

    public static String putJson(String url, String json) throws IOException {
        HttpRequest request = base(url)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return sendText(request);
    }

    public static String postForm(String url, java.util.Map<String, String> form) throws IOException {
        HttpRequest request = base(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form(form), StandardCharsets.UTF_8))
                .build();
        return sendText(request);
    }

    private static String sendText(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status / 100 != 2) {
                throw new ApiException(status, request.uri().toString(), response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断", e);
        }
    }

    public static class ApiException extends IOException {
        private final int status;
        private final String body;

        public ApiException(int status, String url, String body) {
            super("HTTP " + status + " - " + url);
            this.status = status;
            this.body = body == null ? "" : body;
        }

        public int status() {
            return status;
        }

        public String body() {
            return body;
        }
    }

    public static byte[] bytes(String url, int maxBytes) throws IOException {
        HttpRequest request = base(url).GET().build();
        byte[] body = send(request).body();
        if (body.length > maxBytes) {
            throw new IOException("响应过大: " + body.length + " 字节");
        }
        return body;
    }

    public static long download(String url, Path dest, long expectedSize, ProgressSink progress) throws IOException {
        HttpRequest request = base(url).GET().build();
        HttpResponse<InputStream> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断", e);
        }
        int status = response.statusCode();
        if (status / 100 != 2) {
            response.body().close();
            throw new HttpStatusException(status, url);
        }
        long contentLength = response.headers().firstValueAsLong("content-length").orElse(expectedSize);
        if (dest.getParent() != null) {
            Files.createDirectories(dest.getParent());
        }
        long total = 0;
        byte[] buf = new byte[128 * 1024];
        try (InputStream in = response.body(); var out = Files.newOutputStream(dest)) {
            int read;
            while ((read = in.read(buf)) >= 0) {
                out.write(buf, 0, read);
                total += read;
                if (progress != null) {
                    progress.accept(total, contentLength);
                }
            }
        }
        return total;
    }

    private static HttpResponse<byte[]> send(HttpRequest request) throws IOException {
        try {
            HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status / 100 != 2) {
                throw new HttpStatusException(status, request.uri().toString());
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断", e);
        }
    }

    public interface ProgressSink {
        void accept(long done, long total);
    }

    public static class HttpStatusException extends IOException {
        private final int status;
        private final String url;

        public HttpStatusException(int status, String url) {
            super("HTTP " + status + " - " + url);
            this.status = status;
            this.url = url;
        }

        public int status() {
            return status;
        }

        public String url() {
            return url;
        }
    }

    public static String host(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Form-encodes a small map for OAuth style requests. */
    public static String form(java.util.Map<String, String> values) {
        StringBuilder sb = new StringBuilder();
        for (var e : values.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(java.net.URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(java.net.URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    public static Consumer<String> ignore() {
        return s -> {
        };
    }
}
