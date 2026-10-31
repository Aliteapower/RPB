package com.rpb.reservation.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentProofScanResult(
    boolean success,
    boolean replayed,
    String outcome,
    UUID intentId,
    UUID sessionId,
    UUID proofId,
    UUID verificationId,
    String paymentReference,
    BigDecimal expectedAmount,
    PaymentProofOcrFields ocr,
    PaymentProofChecks checks
) {
    public static PaymentProofScanResult noMatch(boolean replayed, PaymentProofOcrFields ocr, PaymentProofChecks checks) {
        return new PaymentProofScanResult(
            true,
            replayed,
            "no_match",
            null,
            null,
            null,
            null,
            null,
            null,
            ocr,
            checks
        );
    }

    public PaymentProofScanResult asReplay() {
        return new PaymentProofScanResult(
            success,
            true,
            outcome,
            intentId,
            sessionId,
            proofId,
            verificationId,
            paymentReference,
            expectedAmount,
            ocr,
            checks
        );
    }
}
