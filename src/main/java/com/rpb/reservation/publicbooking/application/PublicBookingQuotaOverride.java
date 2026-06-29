package com.rpb.reservation.publicbooking.application;

public record PublicBookingQuotaOverride(
    String periodKey,
    String quotaMode,
    Integer quotaPercent,
    Integer tableCount,
    Integer guestCount
) {

    public static PublicBookingQuotaOverride percentage(String periodKey, int quotaPercent) {
        return new PublicBookingQuotaOverride(periodKey, PublicBookingSettings.MODE_PERCENTAGE, quotaPercent, null, null);
    }

    public static PublicBookingQuotaOverride tableCount(String periodKey, int tableCount) {
        return new PublicBookingQuotaOverride(periodKey, PublicBookingSettings.MODE_TABLE_COUNT, null, tableCount, null);
    }

    public static PublicBookingQuotaOverride guestCount(String periodKey, int guestCount) {
        return new PublicBookingQuotaOverride(periodKey, PublicBookingSettings.MODE_GUEST_COUNT, null, null, guestCount);
    }

    public static PublicBookingQuotaOverride closed(String periodKey) {
        return new PublicBookingQuotaOverride(periodKey, PublicBookingSettings.MODE_CLOSED, null, null, null);
    }
}
