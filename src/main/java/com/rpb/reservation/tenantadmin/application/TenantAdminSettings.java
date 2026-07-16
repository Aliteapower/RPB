package com.rpb.reservation.tenantadmin.application;

public record TenantAdminSettings(
    String storeName,
    String timezone,
    String locale,
    String dateFormat,
    String timeFormat,
    String currency,
    int reservationHoldMinutes,
    int queueCallHoldMinutes,
    int expectedDiningMinutes
) {
}
