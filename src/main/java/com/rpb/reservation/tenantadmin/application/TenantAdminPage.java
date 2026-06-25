package com.rpb.reservation.tenantadmin.application;

public record TenantAdminPage(
    int limit,
    int offset,
    int total
) {
}
