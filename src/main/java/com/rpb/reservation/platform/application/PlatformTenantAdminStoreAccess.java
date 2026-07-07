package com.rpb.reservation.platform.application;

import java.util.List;
import java.util.UUID;

public record PlatformTenantAdminStoreAccess(
    List<PlatformTenantStoreOption> stores,
    List<UUID> storeIds,
    UUID defaultStoreId
) {
    public PlatformTenantAdminStoreAccess {
        stores = stores == null ? List.of() : List.copyOf(stores);
        storeIds = storeIds == null ? List.of() : List.copyOf(storeIds);
    }
}
