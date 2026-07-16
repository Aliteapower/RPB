package com.rpb.reservation.tenantadmin.api;

public record TenantAdminSettingsRequest(
    String storeName,
    String timezone,
    String locale,
    String dateFormat,
    String timeFormat,
    String currency,
    Integer reservationHoldMinutes,
    Integer queueCallHoldMinutes,
    Integer expectedDiningMinutes
) {
}
