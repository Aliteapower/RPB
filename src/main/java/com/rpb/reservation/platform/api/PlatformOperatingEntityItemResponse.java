package com.rpb.reservation.platform.api;

import com.rpb.reservation.platform.application.PlatformOperatingEntity;
import java.time.OffsetDateTime;

public record PlatformOperatingEntityItemResponse(
    String id,
    String tenantId,
    String entityCode,
    String displayName,
    String status,
    String defaultLocale,
    String contactPhone,
    String address,
    String principalName,
    boolean deleted,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime deletedAt
) {
    public static PlatformOperatingEntityItemResponse from(PlatformOperatingEntity entity) {
        return new PlatformOperatingEntityItemResponse(
            entity.id().toString(),
            entity.tenantId().toString(),
            entity.entityCode(),
            entity.displayName(),
            entity.status(),
            entity.defaultLocale(),
            entity.contactPhone(),
            entity.address(),
            entity.principalName(),
            entity.deleted(),
            entity.createdAt(),
            entity.updatedAt(),
            entity.deletedAt()
        );
    }
}
