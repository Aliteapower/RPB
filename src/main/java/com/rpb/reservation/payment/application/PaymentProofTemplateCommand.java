package com.rpb.reservation.payment.application;

public record PaymentProofTemplateCommand(
    String bankCode,
    String bankName,
    String locale,
    String templateName,
    String status,
    int priority,
    String layoutJson,
    int version
) {
}
