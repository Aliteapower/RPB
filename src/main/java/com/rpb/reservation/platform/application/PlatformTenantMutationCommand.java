package com.rpb.reservation.platform.application;

import java.util.List;
import java.util.UUID;

public record PlatformTenantMutationCommand(
    String tenantCode,
    String displayName,
    String status,
    String defaultLocale,
    String contactPhone,
    String address,
    String principalName,
    String initialPassword,
    String password,
    List<UUID> adminStoreIds,
    UUID defaultAdminStoreId
) {
}
