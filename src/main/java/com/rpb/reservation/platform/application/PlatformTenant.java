package com.rpb.reservation.platform.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlatformTenant(
    UUID id,
    String tenantCode,
    String displayName,
    String status,
    String defaultLocale,
    String contactPhone,
    String address,
    String principalName,
    UUID logoMediaAssetId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime deletedAt
) {
    public boolean deleted() {
        return deletedAt != null;
    }
}
