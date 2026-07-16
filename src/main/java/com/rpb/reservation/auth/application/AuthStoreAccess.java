package com.rpb.reservation.auth.application;

import java.util.UUID;

public record AuthStoreAccess(
    UUID tenantId,
    String tenantCode,
    UUID operatingEntityId,
    String operatingEntityName,
    UUID storeId,
    String storeCode,
    String storeName,
    String status,
    String locale,
    boolean defaultStore
) {
}
