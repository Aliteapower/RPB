package com.rpb.reservation.payment.application;

public record PaymentProofTemplateTestScanResult(
    PaymentProofTemplate template,
    PaymentProofOcrFields ocr
) {
}
