package org.openpeerpay.edge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
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
    private static final int COLOR_BG = Color.rgb(10, 15, 18);
    private static final int COLOR_PANEL = Color.rgb(18, 27, 30);
    private static final int COLOR_PANEL_2 = Color.rgb(24, 36, 39);
    private static final int COLOR_TEXT = Color.rgb(234, 243, 238);
    private static final int COLOR_MUTED = Color.rgb(139, 156, 150);
    private static final int COLOR_GREEN = Color.rgb(57, 224, 157);
    private static final int COLOR_GOLD = Color.rgb(244, 184, 75);
    private static final int COLOR_CORAL = Color.rgb(255, 111, 97);
    private static final int COLOR_STROKE = Color.rgb(45, 67, 65);

    private TextView statusText;
    private TextView logText;
    private TextView heroStatusText;
    private TextView serverText;
    private TextView accountsText;
    private TextView serviceText;
    private LinearLayout rootView;
    private boolean scanAfterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(COLOR_BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(COLOR_BG);
        }
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
        scrollView.setBackgroundColor(COLOR_BG);
        LinearLayout root = new LinearLayout(this);
        rootView = root;
        root.setOrientation(LinearLayout.VERTICAL);
        applySafeAreaPadding();
        scrollView.addView(root);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("EDGE LISTENER");
        eyebrow.setTextSize(12);
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        eyebrow.setTextColor(COLOR_GREEN);
        eyebrow.setLetterSpacing(0.12f);
        root.addView(eyebrow, matchWrap());

        TextView title = new TextView(this);
        title.setText("PeerPay Edge");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(COLOR_TEXT);
        title.setGravity(Gravity.START);
        title.setPadding(0, dp(4), 0, 0);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("扫码绑定服务端，自动监听微信与支付宝到账。");
        subtitle.setTextSize(14);
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setPadding(0, dp(6), 0, dp(18));
        root.addView(subtitle, matchWrap());

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));
        hero.setBackground(panelBg(COLOR_PANEL, COLOR_STROKE, dp(18)));
        root.addView(hero, matchWrap());

        heroStatusText = new TextView(this);
        heroStatusText.setTextSize(16);
        heroStatusText.setTypeface(Typeface.DEFAULT_BOLD);
        heroStatusText.setTextColor(COLOR_TEXT);
        hero.addView(heroStatusText, matchWrap());

        serverText = smallLine();
        serverText.setPadding(0, dp(10), 0, 0);
        hero.addView(serverText, matchWrap());

        accountsText = smallLine();
        accountsText.setPadding(0, dp(4), 0, 0);
        hero.addView(accountsText, matchWrap());

        serviceText = smallLine();
        serviceText.setPadding(0, dp(4), 0, 0);
        hero.addView(serviceText, matchWrap());

        statusText = new TextView(this);
        statusText.setTextSize(13);
        statusText.setTextColor(COLOR_MUTED);
        statusText.setLineSpacing(3, 1.05f);
        statusText.setPadding(dp(14), dp(14), dp(14), dp(14));
        statusText.setBackground(panelBg(COLOR_PANEL_2, COLOR_STROKE, dp(14)));
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(12);
        root.addView(statusText, statusParams);

        addSectionTitle(root, "绑定");
        addButton(root, "扫码绑定账号", true, view -> ensureCameraAndScan());
        addButton(root, "粘贴配对链接", false, view -> showPasteDialog());

        addSectionTitle(root, "权限");
        addButton(root, "无障碍授权", false, view -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        addButton(root, "通知读取授权", false, view -> openNotificationListenerSettings());
        addButton(root, "忽略电池优化", false, view -> requestBatteryOptimizationExemption());

        addSectionTitle(root, "运行");
        addButton(root, "启动前台服务", false, view -> {
            ForegroundKeepAliveService.start(this);
            appendLog("前台服务已启动");
            refreshStatus();
        });
        addButton(root, "立即发送心跳", false, view -> sendManualHeartbeat());

        logText = new TextView(this);
        logText.setTextSize(13);
        logText.setTextColor(COLOR_MUTED);
        logText.setLineSpacing(3, 1.05f);
        logText.setPadding(dp(14), dp(14), dp(14), dp(14));
        logText.setBackground(panelBg(Color.rgb(14, 21, 23), Color.rgb(34, 50, 49), dp(14)));
        LinearLayout.LayoutParams logParams = matchWrap();
        logParams.topMargin = dp(14);
        root.addView(logText, logParams);

        TextView footnote = new TextView(this);
        footnote.setText("保持前台通知显示，后台监听更稳定。");
        footnote.setTextSize(12);
        footnote.setTextColor(Color.rgb(100, 118, 112));
        footnote.setGravity(Gravity.CENTER);
        footnote.setPadding(0, dp(16), 0, 0);
        root.addView(footnote, matchWrap());

        setContentView(scrollView);
        applySafeAreaPadding();
    }

    private void applySafeAreaPadding() {
        if (rootView == null) {
            return;
        }
        int top = dp(24) + getStatusBarHeight();
        int bottom = dp(24) + getNavigationBarHeight();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                top = dp(24) + insets.getSystemWindowInsetTop();
                bottom = dp(24) + insets.getSystemWindowInsetBottom();
            }
        }
        rootView.setPadding(dp(18), top, dp(18), bottom);
    }

    private void addSectionTitle(LinearLayout root, String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(164, 180, 174));
        title.setPadding(0, dp(18), 0, dp(2));
        root.addView(title, matchWrap());
    }

    private TextView smallLine() {
        TextView view = new TextView(this);
        view.setTextSize(13);
        view.setTextColor(COLOR_MUTED);
        view.setSingleLine(false);
        return view;
    }

    private void addButton(LinearLayout root, String text, boolean primary, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setTextColor(primary ? Color.rgb(5, 20, 17) : COLOR_TEXT);
        button.setBackground(primary
                ? gradientBg(COLOR_GREEN, COLOR_GOLD, dp(14))
                : panelBg(Color.rgb(23, 34, 37), Color.rgb(51, 75, 73), dp(14)));
        button.setOnClickListener(listener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(primary ? dp(4) : dp(1));
        }
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(10);
        root.addView(button, params);
    }

    private GradientDrawable panelBg(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private GradientDrawable gradientBg(int start, int end, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{start, end}
        );
        drawable.setCornerRadius(radius);
        return drawable;
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
        boolean paired = AppConfig.isPaired(this);
        boolean accessibility = isAccessibilityEnabled(this);
        boolean notification = isNotificationListenerEnabled(this);
        heroStatusText.setText((paired ? "● 已绑定" : "● 等待绑定")
                + "  /  " + (accessibility || notification ? "监听在线" : "授权未完成"));
        heroStatusText.setTextColor(paired && (accessibility || notification) ? COLOR_GREEN : paired ? COLOR_GOLD : COLOR_CORAL);
        serverText.setText("服务器  " + emptyAsDash(AppConfig.getServerBaseUrl(this)));
        accountsText.setText("账号  " + describeAccounts());
        serviceText.setText("服务  无障碍 " + (accessibility ? "ON" : "OFF")
                + "  ·  通知读取 " + (notification ? "ON" : "OFF"));

        StringBuilder builder = new StringBuilder();
        builder.append("设备 ID  ").append(AppConfig.getDeviceId(this)).append('\n');
        builder.append("上次心跳  ").append(formatTime(AppConfig.getLastHeartbeat(this))).append('\n');
        builder.append("上次上报  ").append(formatTime(AppConfig.getLastNotification(this)));
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

    private int getStatusBarHeight() {
        return getSystemDimension("status_bar_height");
    }

    private int getNavigationBarHeight() {
        return getSystemDimension("navigation_bar_height");
    }

    private int getSystemDimension(String name) {
        int resourceId = getResources().getIdentifier(name, "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
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
