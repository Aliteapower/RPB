package com.rpb.reservation.reservation.application;

import java.time.LocalTime;

public record ReservationMealPeriodCommand(
    String periodKey,
    String displayName,
    LocalTime startLocalTime,
    LocalTime endLocalTime,
    Boolean crossesNextDay,
    Integer slotIntervalMinutes,
    String status,
    Integer sortOrder,
    Integer version
) {
}
