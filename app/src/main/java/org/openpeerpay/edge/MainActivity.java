package org.openpeerpay.edge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    private static final int REQUEST_CAMERA = 1001;
    private static final int REQUEST_POST_NOTIFICATIONS = 1002;

    private TextView statusText;
    private TextView logText;
    private boolean scanAfterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestPostNotificationPermission();
        ForegroundKeepAliveService.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                enroll(result.getContents());
            } else {
                appendLog("已取消扫码");
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA && scanAfterPermission) {
            scanAfterPermission = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startQrScan();
            } else {
                appendLog("相机权限未授权，无法扫码");
            }
        }
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(24));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("PeerPay Edge");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(24, 32, 28));
        title.setGravity(Gravity.START);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("扫码绑定服务端，监听微信和支付宝收款通知。");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(84, 96, 90));
        subtitle.setPadding(0, dp(6), 0, dp(16));
        root.addView(subtitle, matchWrap());

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setTextColor(Color.rgb(36, 48, 42));
        statusText.setLineSpacing(2, 1.1f);
        statusText.setPadding(dp(14), dp(14), dp(14), dp(14));
        statusText.setBackgroundColor(Color.rgb(239, 247, 243));
        root.addView(statusText, matchWrap());

        addButton(root, "扫码绑定账号", view -> ensureCameraAndScan());
        addButton(root, "粘贴配对链接", view -> showPasteDialog());
        addButton(root, "打开无障碍授权", view -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        addButton(root, "打开通知读取授权", view -> openNotificationListenerSettings());
        addButton(root, "忽略电池优化", view -> requestBatteryOptimizationExemption());
        addButton(root, "启动前台服务", view -> {
            ForegroundKeepAliveService.start(this);
            appendLog("前台服务已启动");
            refreshStatus();
        });
        addButton(root, "立即发送心跳", view -> sendManualHeartbeat());

        logText = new TextView(this);
        logText.setTextSize(13);
        logText.setTextColor(Color.rgb(72, 83, 78));
        logText.setPadding(0, dp(18), 0, 0);
        root.addView(logText, matchWrap());

        setContentView(scrollView);
    }

    private void addButton(LinearLayout root, String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(15);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(10);
        root.addView(button, params);
    }

    private void ensureCameraAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            scanAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            return;
        }
        startQrScan();
    }

    private void startQrScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("扫描 PeerPay 设备配对二维码");
        integrator.setBeepEnabled(false);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    private void showPasteDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setHint("粘贴管理台生成的 pairingUrl");
        new AlertDialog.Builder(this)
                .setTitle("粘贴配对链接")
                .setView(input)
                .setPositiveButton("绑定", (dialog, which) -> enroll(input.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void enroll(String qrContent) {
        appendLog("正在向服务端注册设备...");
        new BackendClient(this).enroll(qrContent, new BackendClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                appendLog("绑定成功，设备密钥已保存");
                Toast.makeText(MainActivity.this, "绑定成功", Toast.LENGTH_SHORT).show();
                refreshStatus();
                sendManualHeartbeat();
            }

            @Override
            public void onError(Exception error) {
                appendLog("绑定失败：" + error.getMessage());
                refreshStatus();
            }
        });
    }

    private void sendManualHeartbeat() {
        appendLog("正在发送心跳...");
        new BackendClient(this).heartbeat(new BackendClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                appendLog("心跳成功");
                refreshStatus();
            }

            @Override
            public void onError(Exception error) {
                appendLog("心跳失败：" + error.getMessage());
                refreshStatus();
            }
        });
    }

    private void refreshStatus() {
        StringBuilder builder = new StringBuilder();
        builder.append("绑定状态：").append(AppConfig.isPaired(this) ? "已绑定" : "未绑定").append('\n');
        builder.append("服务器：").append(emptyAsDash(AppConfig.getServerBaseUrl(this))).append('\n');
        builder.append("设备 ID：").append(AppConfig.getDeviceId(this)).append('\n');
        builder.append("绑定账号：").append(describeAccounts()).append('\n');
        builder.append("无障碍监听：").append(isAccessibilityEnabled(this) ? "已开启" : "未开启").append('\n');
        builder.append("通知读取：").append(isNotificationListenerEnabled(this) ? "已开启" : "未开启").append('\n');
        builder.append("上次心跳：").append(formatTime(AppConfig.getLastHeartbeat(this))).append('\n');
        builder.append("上次上报：").append(formatTime(AppConfig.getLastNotification(this)));
        statusText.setText(builder.toString());
    }

    private String describeAccounts() {
        try {
            JSONArray accounts = new JSONArray(AppConfig.getBoundAccounts(this));
            if (accounts.length() == 0) {
                return "-";
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < accounts.length(); i++) {
                JSONObject account = accounts.optJSONObject(i);
                if (account == null) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append("，");
                }
                builder.append(account.optString("name", account.optString("code", "-")));
                String channel = account.optString("paymentChannel", "");
                if (!channel.isEmpty()) {
                    builder.append("(").append(channel).append(")");
                }
            }
            return builder.length() == 0 ? "-" : builder.toString();
        } catch (Exception ignored) {
            return "-";
        }
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception error) {
            appendLog("无法打开系统设置：" + error.getMessage());
        }
    }

    private void openNotificationListenerSettings() {
        String action = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                ? Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                : "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";
        openSettings(action);
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            appendLog("当前系统不需要单独设置电池优化");
            return;
        }
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        if (manager != null && manager.isIgnoringBatteryOptimizations(getPackageName())) {
            appendLog("已在电池优化白名单中");
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception error) {
            openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        }
    }

    private void requestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
        }
    }

    private void appendLog(String message) {
        String current = logText == null ? "" : logText.getText().toString();
        String line = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()) + "  " + message;
        logText.setText(current.isEmpty() ? line : line + "\n" + current);
    }

    private String formatTime(String value) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        try {
            long timestamp = Long.parseLong(value);
            return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date(timestamp));
        } catch (Exception ignored) {
            return value;
        }
    }

    private String emptyAsDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    static boolean isAccessibilityEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        ComponentName expected = new ComponentName(context, PaymentAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName component = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(component)) {
                return true;
            }
        }
        return false;
    }

    static boolean isNotificationListenerEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) {
            return false;
        }
        String packageName = context.getPackageName();
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName component = ComponentName.unflattenFromString(splitter.next());
            if (component != null && packageName.equals(component.getPackageName())) {
                return true;
            }
        }
        return false;
    }
}
