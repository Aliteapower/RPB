package com.rpb.reservation.reservation.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationTodayViewItem(
    UUID reservationId,
    String reservationCode,
    String status,
    int partySize,
    Instant reservedStartAt,
    Instant reservedEndAt,
    Instant holdUntilAt,
    LocalDate businessDate,
    String customerName,
    String customerNickname,
    String phoneMasked,
    String note
) {
}
