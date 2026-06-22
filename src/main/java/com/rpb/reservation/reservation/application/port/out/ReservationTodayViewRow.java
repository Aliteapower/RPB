package com.rpb.reservation.reservation.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationTodayViewRow(
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
    String phoneE164,
    String note
) {
}
