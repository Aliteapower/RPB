package com.rpb.reservation.platform.api;

import com.rpb.reservation.platform.application.PlatformTenant;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PlatformTenantItemResponse(
    UUID id,
    String tenantCode,
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
    public static PlatformTenantItemResponse from(PlatformTenant tenant) {
        return new PlatformTenantItemResponse(
            tenant.id(),
            tenant.tenantCode(),
            tenant.displayName(),
            tenant.status(),
            tenant.defaultLocale(),
            tenant.contactPhone(),
            tenant.address(),
            tenant.principalName(),
            tenant.deleted(),
            tenant.createdAt(),
            tenant.updatedAt(),
            tenant.deletedAt()
        );
    }
}
