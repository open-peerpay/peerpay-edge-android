package org.openpeerpay.edge;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class PaymentAccessibilityService extends AccessibilityService {
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        ForegroundKeepAliveService.start(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        PaymentReporter.report(this, PaymentEventParser.fromAccessibilityEvent(event));
    }

    @Override
    public void onInterrupt() {
    }
}
