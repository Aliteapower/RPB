package com.rpb.reservation.reservation.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReservationCalendarSummaryResponse(
    boolean success,
    UUID storeId,
    String month,
    String storeTimezone,
    List<DayResponse> days
) {
    public ReservationCalendarSummaryResponse {
        days = days == null ? List.of() : List.copyOf(days);
    }

    public record DayResponse(
        LocalDate businessDate,
        long reservationCount
    ) {
    }
}
