package org.openpeerpay.edge;

final class PaymentEvent {
    final String packageName;
    final String paymentChannel;
    final String rawText;
    final String actualAmount;
    final String source;
    final long eventTime;

    PaymentEvent(String packageName, String paymentChannel, String rawText, String actualAmount, String source, long eventTime) {
        this.packageName = packageName;
        this.paymentChannel = paymentChannel;
        this.rawText = rawText;
        this.actualAmount = actualAmount;
        this.source = source;
        this.eventTime = eventTime;
    }

    String dedupeKey() {
        return packageName + "|" + paymentChannel + "|" + actualAmount + "|" + rawText.hashCode();
    }
}
