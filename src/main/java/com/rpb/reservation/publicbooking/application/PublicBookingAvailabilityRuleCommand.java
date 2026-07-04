package com.rpb.reservation.publicbooking.application;

import java.time.LocalDate;

public record PublicBookingAvailabilityRuleCommand(
    String ruleType,
    LocalDate businessDate,
    Integer dayOfWeek,
    String periodKey,
    String quotaMode,
    Integer quotaPercent,
    Integer tableCount,
    Integer guestCount
) {
}
