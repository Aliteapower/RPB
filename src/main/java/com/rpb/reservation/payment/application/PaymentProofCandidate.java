package com.rpb.reservation.payment.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentProofCandidate(
    UUID intentId,
    UUID sessionId,
    String intentNo,
    String sessionNo,
    int displayNumber,
    LocalDate businessDate,
    String paymentReference,
    BigDecimal amount,
    String currency,
    String intentStatus,
    String sessionStatus,
    String terminalCode,
    String cashierName,
    OffsetDateTime createdAt,
    OffsetDateTime expiresAt
) {
}
