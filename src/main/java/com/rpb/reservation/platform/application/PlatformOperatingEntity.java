package com.rpb.reservation.platform.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlatformOperatingEntity(
    UUID id,
    UUID tenantId,
    String entityCode,
    String displayName,
    String status,
    String defaultLocale,
    String contactPhone,
    String address,
    String principalName,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime deletedAt
) {
    public boolean deleted() {
        return deletedAt != null;
    }
}
