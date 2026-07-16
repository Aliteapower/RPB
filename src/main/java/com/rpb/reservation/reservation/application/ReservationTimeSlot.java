package com.rpb.reservation.reservation.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ReservationTimeSlot(
    UUID periodId,
    String periodKey,
    String displayName,
    LocalDate businessDate,
    LocalTime time,
    Instant startAt,
    boolean nextDay,
    boolean selectable
) {
}
