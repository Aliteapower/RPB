package com.rpb.reservation.tenantadmin.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantAdminProfile(
    UUID tenantId,
    String tenantCode,
    String displayName,
    String status,
    String defaultLocale,
    String contactPhone,
    String address,
    String principalName,
    UUID logoMediaAssetId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
