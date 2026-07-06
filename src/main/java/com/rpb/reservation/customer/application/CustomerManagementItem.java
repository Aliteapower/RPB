package com.rpb.reservation.customer.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerManagementItem(
    UUID id,
    String customerCode,
    String displayName,
    String nickname,
    String phoneE164,
    String email,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
