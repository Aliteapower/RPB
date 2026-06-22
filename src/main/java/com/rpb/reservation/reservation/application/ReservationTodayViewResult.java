package com.rpb.reservation.reservation.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReservationTodayViewResult(
    boolean success,
    ReservationTodayViewError error,
    UUID storeId,
    LocalDate businessDate,
    String storeTimezone,
    String statusFilter,
    List<ReservationTodayViewItem> items
) {

    public ReservationTodayViewResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static ReservationTodayViewResult success(
        UUID storeId,
        LocalDate businessDate,
        String storeTimezone,
        String statusFilter,
        List<ReservationTodayViewItem> items
    ) {
        return new ReservationTodayViewResult(
            true,
            null,
            storeId,
            businessDate,
            storeTimezone,
            statusFilter,
            items
        );
    }

    public static ReservationTodayViewResult failure(ReservationTodayViewError error) {
        return new ReservationTodayViewResult(
            false,
            error,
            null,
            null,
            null,
            null,
            List.of()
        );
    }
}
