package org.openpeerpay.edge;

import android.content.Context;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class PaymentReporter {
    private static final long DEDUPE_WINDOW_MS = 15_000L;
    private static final int MAX_RECENT_EVENTS = 80;
    private static final LinkedHashMap<String, Long> RECENT_EVENTS = new LinkedHashMap<>();

    private PaymentReporter() {
    }

    static void report(Context context, PaymentEvent event) {
        if (event == null || !AppConfig.isPaired(context)) {
            return;
        }
        if (isDuplicate(event)) {
            return;
        }

        new BackendClient(context).reportPayment(event, new BackendClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                AppConfig.markNotification(context);
            }

            @Override
            public void onError(Exception error) {
                // Foreground service heartbeat will surface connectivity through last-seen status.
            }
        });
    }

    private static synchronized boolean isDuplicate(PaymentEvent event) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = RECENT_EVENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > DEDUPE_WINDOW_MS || RECENT_EVENTS.size() > MAX_RECENT_EVENTS) {
                iterator.remove();
            }
        }

        String key = event.dedupeKey();
        Long previous = RECENT_EVENTS.get(key);
        if (previous != null && now - previous <= DEDUPE_WINDOW_MS) {
            return true;
        }
        RECENT_EVENTS.put(key, now);
        return false;
    }
}
