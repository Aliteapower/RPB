package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.TenantAdminTable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantAdminTableItemResponse(
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
    public static TenantAdminTableItemResponse from(TenantAdminTable table) {
        return new TenantAdminTableItemResponse(
            table.id(),
            table.areaId(),
            table.areaName(),
            table.areaSortOrder(),
            table.tableCode(),
            table.tableSortOrder(),
            table.capacity(),
            table.status(),
            table.enabled(),
            table.createdAt(),
            table.updatedAt()
        );
    }
}
