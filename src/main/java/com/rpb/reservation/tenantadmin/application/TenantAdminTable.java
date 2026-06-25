package com.rpb.reservation.tenantadmin.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantAdminTable(
    UUID id,
    String areaName,
    String tableCode,
    int capacity,
    String status,
    boolean enabled,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
