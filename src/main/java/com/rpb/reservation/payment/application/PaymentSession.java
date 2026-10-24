package com.rpb.reservation.payment.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentSession(
    UUID id,
    UUID tenantId,
    UUID storeId,
    UUID intentId,
    String sessionNo,
    int displayNumber,
    LocalDate businessDate,
    String status,
    String qrPayloadsJson,
    OffsetDateTime expiresAt,
    int version
) {
}
