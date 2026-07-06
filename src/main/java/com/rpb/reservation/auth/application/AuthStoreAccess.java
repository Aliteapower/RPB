package com.rpb.reservation.auth.application;

import java.util.UUID;

public record AuthStoreAccess(
    UUID storeId,
    String storeCode,
    String storeName,
    String status,
    String locale,
    boolean defaultStore
) {
}
