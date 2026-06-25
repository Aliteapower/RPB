package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.TenantAdminPage;

public record TenantAdminPageResponse(
    int limit,
    int offset,
    int total
) {
    public static TenantAdminPageResponse from(TenantAdminPage page) {
        return new TenantAdminPageResponse(page.limit(), page.offset(), page.total());
    }
}
