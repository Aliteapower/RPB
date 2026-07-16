package com.rpb.reservation.platform.api;

import com.rpb.reservation.platform.application.PlatformTenantStoreOption;
import java.util.UUID;

public record PlatformTenantStoreOptionResponse(
    UUID storeId,
    UUID operatingEntityId,
    String operatingEntityName,
    String storeCode,
    String storeName,
    String status,
    String locale,
    boolean defaultStore
) {
    public static PlatformTenantStoreOptionResponse from(PlatformTenantStoreOption store) {
        return new PlatformTenantStoreOptionResponse(
            store.storeId(),
            store.operatingEntityId(),
            store.operatingEntityName(),
            store.storeCode(),
            store.storeName(),
            store.status(),
            store.locale(),
            store.defaultStore()
        );
    }
}
