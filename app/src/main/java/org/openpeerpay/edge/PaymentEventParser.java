package org.openpeerpay.edge;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PaymentEventParser {
    static final String WECHAT_PACKAGE = "com.tencent.mm";
    static final String ALIPAY_PACKAGE = "com.eg.android.alipaygphone";

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:￥|¥|RMB|CNY)\\s*(\\d{1,8}(?:,\\d{3})*(?:\\.\\d{1,2})?)|(\\d{1,8}(?:,\\d{3})*(?:\\.\\d{1,2})?)\\s*(?:元|块)");
    private static final Pattern PAYMENT_KEYWORDS = Pattern.compile("到账|收款|收钱|已收|入账|付款|支付成功|二维码收款|转账收款|向你付款");

    private PaymentEventParser() {
    }

    static PaymentEvent fromAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return null;
        }

        String packageName = safe(event.getPackageName()).toLowerCase(Locale.US);
        if (!isSupportedPackage(packageName)) {
            return null;
        }

        List<CharSequence> chunks = new ArrayList<>();
        chunks.add(event.getContentDescription());
        chunks.add(event.getBeforeText());
        chunks.add(event.getClassName());
        chunks.addAll(event.getText());

        Parcelable data = event.getParcelableData();
        if (data instanceof Notification) {
            appendNotificationExtras(chunks, (Notification) data);
        }

        String rawText = normalize(chunks);
        String amount = extractAmount(rawText);
        if (!looksLikePayment(rawText, amount)) {
            return null;
        }
        return new PaymentEvent(packageName, channelForPackage(packageName), rawText, amount, "accessibility", event.getEventTime());
    }

    static PaymentEvent fromNotification(String packageName, Notification notification, String source) {
        String normalizedPackage = safe(packageName).toLowerCase(Locale.US);
        if (!isSupportedPackage(normalizedPackage) || notification == null) {
            return null;
        }

        List<CharSequence> chunks = new ArrayList<>();
        chunks.add(notification.tickerText);
        appendNotificationExtras(chunks, notification);
        String rawText = normalize(chunks);
        String amount = extractAmount(rawText);
        if (!looksLikePayment(rawText, amount)) {
            return null;
        }
        return new PaymentEvent(normalizedPackage, channelForPackage(normalizedPackage), rawText, amount, source, System.currentTimeMillis());
    }

    static boolean isSupportedPackage(String packageName) {
        String text = safe(packageName).toLowerCase(Locale.US);
        return WECHAT_PACKAGE.equals(text) || ALIPAY_PACKAGE.equals(text);
    }

    static String channelForPackage(String packageName) {
        String text = safe(packageName).toLowerCase(Locale.US);
        return WECHAT_PACKAGE.equals(text) ? "wechat" : "alipay";
    }

    private static void appendNotificationExtras(List<CharSequence> chunks, Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) {
            return;
        }
        chunks.add(extras.getCharSequence(Notification.EXTRA_TITLE));
        chunks.add(extras.getCharSequence(Notification.EXTRA_TEXT));
        chunks.add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        chunks.add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        chunks.add(extras.getCharSequence(Notification.EXTRA_INFO_TEXT));
        chunks.add(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) {
                chunks.add(line);
            }
        }
    }

    private static boolean looksLikePayment(String rawText, String amount) {
        return rawText != null
                && !rawText.isEmpty()
                && amount != null
                && PAYMENT_KEYWORDS.matcher(rawText).find();
    }

    private static String extractAmount(String rawText) {
        Matcher matcher = AMOUNT_PATTERN.matcher(rawText);
        while (matcher.find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (value == null) {
                continue;
            }
            value = value.replace(",", "");
            if (value.length() > 0) {
                return value;
            }
        }
        return null;
    }

    private static String normalize(List<CharSequence> chunks) {
        StringBuilder builder = new StringBuilder();
        for (CharSequence chunk : chunks) {
            String text = safe(chunk).trim();
            if (text.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(text);
        }
        return builder.toString().replaceAll("\\s+", " ").trim();
    }

    private static String safe(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
