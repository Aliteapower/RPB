package com.rpb.reservation.publicbooking.application;

public record PublicBookingSettings(
    boolean enabled,
    boolean requireCustomerLogin,
    String defaultQuotaMode,
    Integer defaultQuotaPercent,
    Integer defaultTableCount,
    Integer defaultGuestCount,
    int minLeadMinutes,
    int maxAdvanceDays
) {
    public static final String MODE_PERCENTAGE = "percentage";
    public static final String MODE_TABLE_COUNT = "table_count";
    public static final String MODE_GUEST_COUNT = "guest_count";
    public static final String MODE_CLOSED = "closed";

    public static PublicBookingSettings enabledPercentage(int quotaPercent) {
        return new PublicBookingSettings(true, true, MODE_PERCENTAGE, quotaPercent, null, null, 0, 30);
    }

    public static PublicBookingSettings disabled() {
        return new PublicBookingSettings(false, true, MODE_PERCENTAGE, 0, null, null, 0, 30);
    }
}
