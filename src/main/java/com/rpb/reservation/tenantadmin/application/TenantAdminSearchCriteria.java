package com.rpb.reservation.tenantadmin.application;

public record TenantAdminSearchCriteria(
    String keyword,
    int limit,
    int offset
) {
}
