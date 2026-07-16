package com.rpb.reservation.platform.api;

import java.util.List;
import java.util.UUID;

public record PlatformTenantMutationRequest(
    String tenantCode,
    String displayName,
    String status,
    String defaultLocale,
    String contactPhone,
    String address,
    String principalName,
    String initialPassword,
    String password,
    String onboardingMode,
    List<UUID> adminStoreIds,
    UUID defaultAdminStoreId
) {
}
