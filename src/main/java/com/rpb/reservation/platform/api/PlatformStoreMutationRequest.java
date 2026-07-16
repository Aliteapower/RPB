package com.rpb.reservation.platform.api;

import java.util.UUID;

public record PlatformStoreMutationRequest(
    UUID operatingEntityId,
    String storeCode,
    String storeName,
    String status,
    String timezone,
    String locale,
    String dateFormat,
    String timeFormat,
    String currency,
    String adminUsername,
    String adminPassword
) {
}
