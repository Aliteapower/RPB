package com.rpb.reservation.reservation.application.command;

import java.time.Instant;
import java.util.UUID;

public record CheckInReservationCommand(
    UUID tenantId,
    UUID storeId,
    UUID reservationId,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    Instant arrivedAt,
    String reasonCode,
    String note
) {
}
