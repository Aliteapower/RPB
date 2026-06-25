package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.TenantAdminTable;

public record TenantAdminTableResponse(
    boolean success,
    TenantAdminTableItemResponse table
) {
    public static TenantAdminTableResponse from(TenantAdminTable table) {
        return new TenantAdminTableResponse(true, TenantAdminTableItemResponse.from(table));
    }
}
