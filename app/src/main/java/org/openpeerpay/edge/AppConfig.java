package org.openpeerpay.edge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.UUID;

final class AppConfig {
    private static final String PREFS = "peerpay_edge";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_SERVER_BASE_URL = "server_base_url";
    private static final String KEY_DEVICE_SECRET = "device_secret";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_BOUND_ACCOUNTS = "bound_accounts";
    private static final String KEY_LAST_HEARTBEAT = "last_heartbeat";
    private static final String KEY_LAST_NOTIFICATION = "last_notification";

    private AppConfig() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String getDeviceId(Context context) {
        SharedPreferences prefs = prefs(context);
        String existing = prefs.getString(KEY_DEVICE_ID, null);
        if (existing != null && !existing.trim().isEmpty()) {
            return existing;
        }

        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String suffix = androidId == null || androidId.trim().isEmpty()
                ? UUID.randomUUID().toString().replace("-", "")
                : androidId.trim().toLowerCase(Locale.US);
        String deviceId = "android-" + suffix;
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        return deviceId;
    }

    static boolean isPaired(Context context) {
        return !getServerBaseUrl(context).isEmpty() && !getDeviceSecret(context).isEmpty();
    }

    static String getServerBaseUrl(Context context) {
        return prefs(context).getString(KEY_SERVER_BASE_URL, "");
    }

    static String getDeviceSecret(Context context) {
        return prefs(context).getString(KEY_DEVICE_SECRET, "");
    }

    static String getDeviceName(Context context) {
        String saved = prefs(context).getString(KEY_DEVICE_NAME, "");
        return saved == null || saved.trim().isEmpty() ? defaultDeviceName() : saved;
    }

    static String getBoundAccounts(Context context) {
        return prefs(context).getString(KEY_BOUND_ACCOUNTS, "[]");
    }

    static String getLastHeartbeat(Context context) {
        return prefs(context).getString(KEY_LAST_HEARTBEAT, "");
    }

    static String getLastNotification(Context context) {
        return prefs(context).getString(KEY_LAST_NOTIFICATION, "");
    }

    static void saveEnrollment(Context context, String serverBaseUrl, String deviceSecret, JSONObject device) {
        String deviceName = device == null ? "" : device.optString("name", "");
        JSONArray accounts = device == null ? new JSONArray() : device.optJSONArray("paymentAccounts");
        prefs(context).edit()
                .putString(KEY_SERVER_BASE_URL, stripTrailingSlash(serverBaseUrl))
                .putString(KEY_DEVICE_SECRET, deviceSecret)
                .putString(KEY_DEVICE_NAME, deviceName == null || deviceName.trim().isEmpty() ? defaultDeviceName() : deviceName)
                .putString(KEY_BOUND_ACCOUNTS, accounts == null ? "[]" : accounts.toString())
                .apply();
    }

    static void markHeartbeat(Context context) {
        prefs(context).edit().putString(KEY_LAST_HEARTBEAT, Long.toString(System.currentTimeMillis())).apply();
    }

    static void markNotification(Context context) {
        prefs(context).edit().putString(KEY_LAST_NOTIFICATION, Long.toString(System.currentTimeMillis())).apply();
    }

    static String getAppVersion(Context context) {
        try {
            String versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            return versionName == null || versionName.trim().isEmpty() ? "0.1.0" : versionName;
        } catch (Exception ignored) {
            return "0.1.0";
        }
    }

    static String defaultDeviceName() {
        String maker = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        String name = (maker + " " + model).trim();
        return name.isEmpty() ? "Android 收款机" : name;
    }

    static String stripTrailingSlash(String value) {
        String text = value == null ? "" : value.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
