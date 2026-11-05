package com.rpb.reservation.payment.api;

public record PaymentProofTemplateRequest(
    String bankCode,
    String bankName,
    String locale,
    String templateName,
    String status,
    Integer priority,
    String layoutJson,
    Integer version
) {
}
