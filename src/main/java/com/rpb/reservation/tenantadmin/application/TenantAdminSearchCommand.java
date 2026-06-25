package com.rpb.reservation.tenantadmin.application;

public record TenantAdminSearchCommand(
    String keyword,
    String limit,
    String offset
) {
}
