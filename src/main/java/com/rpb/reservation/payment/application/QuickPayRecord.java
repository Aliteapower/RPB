package com.rpb.reservation.payment.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QuickPayRecord(
    UUID intentId,
    UUID sessionId,
    String intentNo,
    String sessionNo,
    int displayNumber,
    LocalDate businessDate,
    BigDecimal amount,
    String currency,
    String paymentReference,
    String intentStatus,
    String sessionStatus,
    String terminalCode,
    String cashierName,
    OffsetDateTime createdAt,
    OffsetDateTime expiresAt
) {
}
