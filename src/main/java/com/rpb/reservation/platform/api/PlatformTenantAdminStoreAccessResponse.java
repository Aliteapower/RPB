package com.rpb.reservation.platform.api;

import com.rpb.reservation.platform.application.PlatformTenantAdminStoreAccess;
import java.util.List;
import java.util.UUID;

public record PlatformTenantAdminStoreAccessResponse(
    boolean success,
    List<PlatformTenantStoreOptionResponse> stores,
    List<UUID> storeIds,
    UUID defaultStoreId
) {
    public static PlatformTenantAdminStoreAccessResponse from(PlatformTenantAdminStoreAccess access) {
        return new PlatformTenantAdminStoreAccessResponse(
            true,
            access.stores().stream().map(PlatformTenantStoreOptionResponse::from).toList(),
            access.storeIds(),
            access.defaultStoreId()
        );
    }
}
