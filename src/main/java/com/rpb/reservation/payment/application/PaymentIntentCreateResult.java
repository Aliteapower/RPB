package com.rpb.reservation.payment.application;

public record PaymentIntentCreateResult(
    boolean success,
    boolean replayed,
    PaymentIntent intent,
    PaymentSession session,
    int nextDisplayNumber
) {
    public PaymentIntentCreateResult asReplay() {
        return new PaymentIntentCreateResult(success, true, intent, session, nextDisplayNumber);
    }
}
