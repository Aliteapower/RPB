package com.rpb.reservation.customerauth.application;

import java.time.Instant;
import java.util.UUID;

public record CustomerEmailDeliveryMessage(
    UUID tenantId,
    UUID storeId,
    String toEmail,
    String code,
    Instant expiresAt
) {
}
