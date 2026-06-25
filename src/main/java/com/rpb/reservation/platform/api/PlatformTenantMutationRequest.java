package com.rpb.reservation.platform.api;

public record PlatformTenantMutationRequest(
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
