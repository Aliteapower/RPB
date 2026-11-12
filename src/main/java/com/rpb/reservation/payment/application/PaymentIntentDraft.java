package com.rpb.reservation.payment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentIntentDraft(
    String intentNo,
    String sourceType,
    UUID sourceId,
    String method,
    BigDecimal amount,
    String currency,
    String paymentReference,
    OffsetDateTime expiresAt,
    String idempotencyKey,
    String metadataJson,
    UUID createdBy
) {
}
