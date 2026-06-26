package com.rpb.reservation.tenantadmin.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantAdminTable(
    UUID id,
    UUID areaId,
    String areaName,
    int areaSortOrder,
    String tableCode,
    int tableSortOrder,
    int capacity,
    String status,
    boolean enabled,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
