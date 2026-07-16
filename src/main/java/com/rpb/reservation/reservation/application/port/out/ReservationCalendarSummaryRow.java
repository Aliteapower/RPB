package com.rpb.reservation.reservation.application.port.out;

import java.time.LocalDate;

public record ReservationCalendarSummaryRow(
    LocalDate businessDate,
    long reservationCount
) {
}
