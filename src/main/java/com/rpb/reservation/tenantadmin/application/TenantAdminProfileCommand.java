package com.rpb.reservation.tenantadmin.application;

public record TenantAdminProfileCommand(
    String displayName,
    String defaultLocale,
    String contactPhone,
    String address,
    String principalName
) {
}
