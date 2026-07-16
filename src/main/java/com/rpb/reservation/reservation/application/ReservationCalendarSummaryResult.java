package com.rpb.reservation.reservation.application;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReservationCalendarSummaryResult(
    boolean success,
    ReservationTodayViewError error,
    UUID storeId,
    YearMonth month,
    String storeTimezone,
    List<ReservationCalendarSummaryDay> days
) {
    public ReservationCalendarSummaryResult {
        days = days == null ? List.of() : List.copyOf(days);
    }

    public static ReservationCalendarSummaryResult success(
        UUID storeId,
        YearMonth month,
        String storeTimezone,
        List<ReservationCalendarSummaryDay> days
    ) {
        return new ReservationCalendarSummaryResult(
            true,
            null,
            Objects.requireNonNull(storeId, "reservation_calendar_summary_store_id_required"),
            Objects.requireNonNull(month, "reservation_calendar_summary_month_required"),
            Objects.requireNonNull(storeTimezone, "reservation_calendar_summary_timezone_required"),
            days
        );
    }

    public static ReservationCalendarSummaryResult failure(ReservationTodayViewError error) {
        return new ReservationCalendarSummaryResult(
            false,
            Objects.requireNonNull(error, "reservation_calendar_summary_error_required"),
            null,
            null,
            null,
            List.of()
        );
    }
}
