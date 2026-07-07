package com.rpb.reservation.platform.application;

import java.util.UUID;

public record PlatformStoreMutationCommand(
    UUID operatingEntityId,
    String storeCode,
    String storeName,
    String status,
    String timezone,
    String locale,
    String dateFormat,
    String timeFormat,
    String currency
) {
}
