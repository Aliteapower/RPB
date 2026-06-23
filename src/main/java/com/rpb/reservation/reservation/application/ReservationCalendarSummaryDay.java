package com.rpb.reservation.reservation.application;

import java.time.LocalDate;
import java.util.Objects;

public record ReservationCalendarSummaryDay(
    LocalDate businessDate,
    long reservationCount
) {
    public ReservationCalendarSummaryDay {
        Objects.requireNonNull(businessDate, "reservation_calendar_summary_business_date_required");
        if (reservationCount < 0) {
            throw new IllegalArgumentException("reservation_calendar_summary_count_must_not_be_negative");
        }
    }
}
