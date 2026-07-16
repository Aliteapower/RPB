package com.rpb.reservation.tenantadmin.api;

public record TenantAdminProfileRequest(
    String displayName,
    String defaultLocale,
    String contactPhone,
    String address,
    String principalName
) {
}
