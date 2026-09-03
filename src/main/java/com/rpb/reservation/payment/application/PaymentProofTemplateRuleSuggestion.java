package com.rpb.reservation.payment.application;

public record PaymentProofTemplateRuleSuggestion(
    String bankCode,
    String bankName,
    String locale,
    String templateName,
    String suggestedLayoutJson,
    PaymentProofOcrFields ocr
) {
}
