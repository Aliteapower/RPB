package com.rpb.reservation.platform.application;

import java.util.UUID;

public record PlatformTenantStoreOption(
    UUID storeId,
    String storeCode,
    String storeName,
    String status,
    String locale,
    boolean defaultStore
) {
}
