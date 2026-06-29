package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationMealPeriod;
import java.util.UUID;

public record ReservationMealPeriodResponse(
    UUID id,
    String periodKey,
    String displayName,
    String startLocalTime,
    String endLocalTime,
    boolean crossesNextDay,
    int slotIntervalMinutes,
    String status,
    int sortOrder,
    int version
) {
    public static ReservationMealPeriodResponse from(ReservationMealPeriod period) {
        return new ReservationMealPeriodResponse(
            period.id(),
            period.periodKey(),
            period.displayName(),
            period.startLocalTime().toString(),
            period.endLocalTime().toString(),
            period.crossesNextDay(),
            period.slotIntervalMinutes(),
            period.status(),
            period.sortOrder(),
            period.version()
        );
    }
}
