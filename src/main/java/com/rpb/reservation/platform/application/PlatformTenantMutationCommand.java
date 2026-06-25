package com.rpb.reservation.platform.application;

public record PlatformTenantMutationCommand(
    String tenantCode,
    String displayName,
    String status,
    String defaultLocale,
    String contactPhone,
    String address,
    String principalName,
    String initialPassword,
    String password
) {
}
