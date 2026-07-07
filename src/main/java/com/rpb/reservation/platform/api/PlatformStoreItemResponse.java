package com.rpb.reservation.platform.api;

import com.rpb.reservation.platform.application.PlatformStore;
import java.time.OffsetDateTime;

public record PlatformStoreItemResponse(
    String id,
    String tenantId,
    String operatingEntityId,
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
    boolean deleted,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime deletedAt
) {
    public static PlatformStoreItemResponse from(PlatformStore store) {
        return new PlatformStoreItemResponse(
            store.id().toString(),
            store.tenantId().toString(),
            store.operatingEntityId() == null ? null : store.operatingEntityId().toString(),
            store.operatingEntityCode(),
            store.operatingEntityName(),
            store.storeCode(),
            store.storeName(),
            store.status(),
            store.timezone(),
            store.locale(),
            store.dateFormat(),
            store.timeFormat(),
            store.currency(),
            store.deleted(),
            store.createdAt(),
            store.updatedAt(),
            store.deletedAt()
        );
    }
}
