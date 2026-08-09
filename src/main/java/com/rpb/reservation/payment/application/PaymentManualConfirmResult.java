package com.rpb.reservation.payment.application;

public record PaymentManualConfirmResult(
    boolean success,
    boolean replayed,
    boolean alreadyConfirmed,
    PaymentIntent intent,
    PaymentSession session
) {
    public PaymentManualConfirmResult asReplay() {
        return new PaymentManualConfirmResult(success, true, alreadyConfirmed, intent, session);
    }
}
