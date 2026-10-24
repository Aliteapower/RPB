package com.rpb.reservation.payment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentProofOcrFields(
    String extractedReference,
    BigDecimal extractedAmount,
    OffsetDateTime extractedPaidAt,
    String bankCode,
    boolean successDetected,
    BigDecimal confidence,
    String rawText,
    String rawJson
) {
}
