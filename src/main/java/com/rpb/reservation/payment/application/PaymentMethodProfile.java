package com.rpb.reservation.payment.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentMethodProfile(
    UUID id,
    UUID tenantId,
    UUID storeId,
    String method,
    String status,
    String paynowType,
    String paynowMobile,
    String paynowUen,
    String merchantName,
    String currency,
    String configJson,
    int version,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
