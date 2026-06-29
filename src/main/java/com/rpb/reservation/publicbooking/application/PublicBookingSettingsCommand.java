package com.rpb.reservation.publicbooking.application;

public record PublicBookingSettingsCommand(
    Boolean enabled,
    Boolean requireCustomerLogin,
    String defaultQuotaMode,
    Integer defaultQuotaPercent,
    Integer defaultTableCount,
    Integer defaultGuestCount,
    Integer minLeadMinutes,
    Integer maxAdvanceDays
) {
}
