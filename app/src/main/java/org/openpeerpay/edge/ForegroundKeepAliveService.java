package org.openpeerpay.edge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONObject;

public class ForegroundKeepAliveService extends Service {
    private static final String CHANNEL_ID = "peerpay_keep_alive";
    private static final int NOTIFICATION_ID = 2101;
    private static final long HEARTBEAT_INTERVAL_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            sendHeartbeat();
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };

    public static void start(Context context) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, ForegroundKeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent);
            } else {
                appContext.startService(intent);
            }
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        handler.removeCallbacks(heartbeatRunnable);
        handler.post(heartbeatRunnable);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(heartbeatRunnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendHeartbeat() {
        if (!AppConfig.isPaired(this)) {
            refreshNotification();
            return;
        }

        new BackendClient(this).heartbeat(new BackendClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                refreshNotification();
            }

            @Override
            public void onError(Exception error) {
                refreshNotification();
            }
        });
    }

    private void refreshNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        String title = AppConfig.isPaired(this)
                ? getString(R.string.foreground_title_bound)
                : getString(R.string.foreground_title_unbound);
        String text = AppConfig.isPaired(this)
                ? getString(R.string.foreground_text)
                : "打开应用扫码绑定服务端账号。";

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_peerpay)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setShowWhen(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_SERVICE);
        }
        return builder.build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.foreground_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }
}
