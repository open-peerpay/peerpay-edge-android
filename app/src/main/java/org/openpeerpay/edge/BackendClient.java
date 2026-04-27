package org.openpeerpay.edge;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class BackendClient {
    interface Callback {
        void onSuccess(JSONObject response);

        void onError(Exception error);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private final Context context;

    BackendClient(Context context) {
        this.context = context.getApplicationContext();
    }

    void enroll(String qrContent, Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                PairingPayload pairing = PairingPayload.parse(qrContent);
                JSONObject body = new JSONObject();
                body.put("enrollmentToken", pairing.token);
                body.put("deviceId", AppConfig.getDeviceId(context));
                body.put("name", AppConfig.getDeviceName(context));
                body.put("appVersion", AppConfig.getAppVersion(context));
                body.put("metadata", deviceMetadata());

                HttpResult result = postJson(pairing.enrollUrl, body.toString(), null);
                if (!result.isSuccessful()) {
                    throw new IllegalStateException("配对失败 HTTP " + result.statusCode + ": " + result.body);
                }

                JSONObject response = new JSONObject(result.body);
                JSONObject payload = unwrapPayload(response);
                String secret = firstNonEmpty(
                        payload.optString("deviceSecret", ""),
                        payload.optString("deviceSecrect", ""),
                        payload.optString("secret", "")
                );
                if (secret.trim().isEmpty()) {
                    throw new IllegalStateException("服务端未返回 deviceSecret，响应内容：" + result.body);
                }
                AppConfig.saveEnrollment(context, pairing.serverBaseUrl, secret, payload.optJSONObject("device"));
                ForegroundKeepAliveService.start(context);
                postSuccess(callback, payload);
            } catch (Exception error) {
                postError(callback, error);
            }
        });
    }

    void heartbeat(Callback callback) {
        if (!AppConfig.isPaired(context)) {
            postError(callback, new IllegalStateException("设备尚未扫码绑定"));
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name", AppConfig.getDeviceName(context));
                body.put("appVersion", AppConfig.getAppVersion(context));
                body.put("metadata", deviceMetadata());

                JSONObject response = signedPost("/api/android/heartbeat", body);
                AppConfig.markHeartbeat(context);
                postSuccess(callback, response);
            } catch (Exception error) {
                postError(callback, error);
            }
        });
    }

    void reportPayment(PaymentEvent event, Callback callback) {
        if (!AppConfig.isPaired(context)) {
            postError(callback, new IllegalStateException("设备尚未扫码绑定"));
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("deviceId", AppConfig.getDeviceId(context));
                body.put("paymentChannel", event.paymentChannel);
                body.put("channel", event.source);
                body.put("packageName", event.packageName);
                body.put("rawText", event.rawText);
                body.put("text", event.rawText);
                if (event.actualAmount != null && !event.actualAmount.isEmpty()) {
                    body.put("actualAmount", event.actualAmount);
                }

                JSONObject response = signedPost("/api/android/notifications", body);
                AppConfig.markNotification(context);
                postSuccess(callback, response);
            } catch (Exception error) {
                postError(callback, error);
            }
        });
    }

    private JSONObject signedPost(String path, JSONObject body) throws Exception {
        String base = AppConfig.stripTrailingSlash(AppConfig.getServerBaseUrl(context));
        String fullUrl = base + path;
        String bodyText = body.toString();
        String timestamp = Long.toString(System.currentTimeMillis() / 1000L);
        String nonce = Crypto.nonce();
        String canonical = "POST\n"
                + new URL(fullUrl).getPath() + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + Crypto.sha256Hex(bodyText);
        String signature = Crypto.hmacSha256Hex(AppConfig.getDeviceSecret(context), canonical);

        Map<String, String> headers = new HashMap<>();
        headers.put("x-peerpay-device-id", AppConfig.getDeviceId(context));
        headers.put("x-peerpay-timestamp", timestamp);
        headers.put("x-peerpay-nonce", nonce);
        headers.put("x-peerpay-signature", signature);

        HttpResult result = postJson(fullUrl, bodyText, headers);
        if (!result.isSuccessful()) {
            throw new IllegalStateException("服务端请求失败 HTTP " + result.statusCode + ": " + result.body);
        }
        return result.body.isEmpty() ? new JSONObject() : new JSONObject(result.body);
    }

    private static JSONObject unwrapPayload(JSONObject response) {
        JSONObject data = response.optJSONObject("data");
        if (data != null) {
            return data;
        }
        JSONObject result = response.optJSONObject("result");
        if (result != null) {
            return result;
        }
        return response;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private HttpResult postJson(String urlText, String bodyText, Map<String, String> headers) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("content-type", "application/json; charset=utf-8");
        connection.setRequestProperty("accept", "application/json");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        byte[] bytes = bodyText.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }

        int status = connection.getResponseCode();
        InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String responseBody = readStream(input);
        connection.disconnect();
        return new HttpResult(status, responseBody);
    }

    private JSONObject deviceMetadata() throws Exception {
        JSONObject metadata = new JSONObject();
        metadata.put("sdkInt", Build.VERSION.SDK_INT);
        metadata.put("release", Build.VERSION.RELEASE);
        metadata.put("manufacturer", Build.MANUFACTURER);
        metadata.put("model", Build.MODEL);
        metadata.put("packageName", context.getPackageName());
        metadata.put("accessibilityEnabled", MainActivity.isAccessibilityEnabled(context));
        metadata.put("notificationListenerEnabled", MainActivity.isNotificationListenerEnabled(context));
        return metadata;
    }

    private static String readStream(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static void postSuccess(Callback callback, JSONObject response) {
        if (callback != null) {
            MAIN.post(() -> callback.onSuccess(response));
        }
    }

    private static void postError(Callback callback, Exception error) {
        if (callback != null) {
            MAIN.post(() -> callback.onError(error));
        }
    }

    private static final class HttpResult {
        final int statusCode;
        final String body;

        HttpResult(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }

        boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    private static final class PairingPayload {
        final String enrollUrl;
        final String serverBaseUrl;
        final String token;

        private PairingPayload(String enrollUrl, String serverBaseUrl, String token) {
            this.enrollUrl = enrollUrl;
            this.serverBaseUrl = serverBaseUrl;
            this.token = token;
        }

        static PairingPayload parse(String raw) throws Exception {
            String text = raw == null ? "" : raw.trim();
            if (text.isEmpty()) {
                throw new IllegalArgumentException("二维码内容为空");
            }

            if (text.startsWith("{")) {
                JSONObject json = new JSONObject(text);
                String pairingUrl = json.optString("pairingUrl", json.optString("enrollUrl", ""));
                if (!pairingUrl.isEmpty()) {
                    return parse(pairingUrl);
                }
                String server = json.optString("serverUrl", json.optString("serverBaseUrl", ""));
                String token = json.optString("token", json.optString("enrollmentToken", ""));
                if (!server.isEmpty() && !token.isEmpty()) {
                    String base = originOf(server);
                    return new PairingPayload(base + "/api/android/enroll?token=" + Uri.encode(token), base, token);
                }
            }

            Uri uri = Uri.parse(text);
            String scheme = uri.getScheme();
            if ("peerpay".equalsIgnoreCase(scheme)) {
                String server = uri.getQueryParameter("server");
                String token = uri.getQueryParameter("token");
                if (server == null || token == null) {
                    throw new IllegalArgumentException("PeerPay 二维码缺少 server 或 token");
                }
                String base = originOf(server);
                return new PairingPayload(base + "/api/android/enroll?token=" + Uri.encode(token), base, token);
            }

            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("只支持 PeerPay 配对链接二维码");
            }

            String token = uri.getQueryParameter("token");
            if (token == null || token.trim().isEmpty()) {
                throw new IllegalArgumentException("配对链接缺少 token");
            }
            String base = uri.getScheme() + "://" + uri.getEncodedAuthority();
            return new PairingPayload(text, base, token);
        }

        private static String originOf(String value) throws Exception {
            URL url = new URL(value);
            StringBuilder builder = new StringBuilder();
            builder.append(url.getProtocol()).append("://").append(url.getHost());
            if (url.getPort() > 0) {
                builder.append(':').append(url.getPort());
            }
            return builder.toString();
        }
    }
}
