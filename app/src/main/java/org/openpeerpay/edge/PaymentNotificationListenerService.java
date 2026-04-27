package org.openpeerpay.edge;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class PaymentNotificationListenerService extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        ForegroundKeepAliveService.start(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }
        PaymentReporter.report(this, PaymentEventParser.fromNotification(
                sbn.getPackageName(),
                sbn.getNotification(),
                "notification_listener"
        ));
    }
}
