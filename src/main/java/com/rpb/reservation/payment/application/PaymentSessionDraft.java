package com.rpb.reservation.payment.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentSessionDraft(
    String sessionNo,
    int displayNumber,
    LocalDate businessDate,
    String terminalCode,
    String cashierName,
    String qrPayloadsJson,
    OffsetDateTime expiresAt,
    String idempotencyKey,
    UUID createdBy
) {
}
