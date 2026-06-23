package com.rpb.reservation.reservation.application.command;

import java.time.Instant;
import java.util.UUID;

public record CancelReservationCommand(
    UUID tenantId,
    UUID storeId,
    UUID reservationId,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    Instant cancelledAt,
    String reasonCode,
    String note
) {
}
