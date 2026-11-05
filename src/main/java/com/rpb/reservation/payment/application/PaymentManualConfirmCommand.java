package com.rpb.reservation.payment.application;

public record PaymentManualConfirmCommand(
    String sessionNo,
    String idempotencyKey,
    String terminalCode
) {
}
