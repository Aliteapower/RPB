package com.rpb.reservation.publicbooking.application;

import java.time.LocalDate;
import java.util.UUID;

public record PublicBookingAvailabilityRule(
    UUID id,
    String ruleType,
    LocalDate businessDate,
    Integer dayOfWeek,
    String periodKey,
    String quotaMode,
    Integer quotaPercent,
    Integer tableCount,
    Integer guestCount
) {
    public static final String TYPE_WEEKLY = "weekly";
    public static final String TYPE_DATE_EXCEPTION = "date_exception";
}
