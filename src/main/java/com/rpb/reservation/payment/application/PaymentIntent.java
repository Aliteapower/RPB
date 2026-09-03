package com.rpb.reservation.payment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentIntent(
    UUID id,
    UUID tenantId,
    UUID storeId,
    String intentNo,
    String sourceType,
    UUID sourceId,
    String method,
    BigDecimal amount,
    String currency,
    String paymentReference,
    String status,
    OffsetDateTime expiresAt,
    int version
) {
}
