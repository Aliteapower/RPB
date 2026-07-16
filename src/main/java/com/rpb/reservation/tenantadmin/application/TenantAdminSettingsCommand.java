package com.rpb.reservation.tenantadmin.application;

public record TenantAdminSettingsCommand(
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
