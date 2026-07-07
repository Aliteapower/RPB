package com.rpb.reservation.platformbilling.application;

import java.util.UUID;

public record BillableStore(
    UUID storeId,
    String storeCode,
    String storeName
) {
}
