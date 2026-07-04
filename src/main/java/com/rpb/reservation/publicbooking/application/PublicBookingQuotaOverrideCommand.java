package com.rpb.reservation.publicbooking.application;

import java.time.LocalDate;

public record PublicBookingQuotaOverrideCommand(
    LocalDate businessDate,
    String periodKey,
    String quotaMode,
    Integer quotaPercent,
    Integer tableCount,
    Integer guestCount
) {
}
