package com.rpb.reservation.platform.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlatformStore(
    UUID id,
    UUID tenantId,
    UUID operatingEntityId,
    String operatingEntityCode,
    String operatingEntityName,
    String storeCode,
    String storeName,
    String status,
    String timezone,
    String locale,
    String dateFormat,
    String timeFormat,
    String currency,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime deletedAt
) {
    public boolean deleted() {
        return deletedAt != null;
    }
}
