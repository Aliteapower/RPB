package com.rpb.reservation.reservation.application.command;

import java.time.Instant;
import java.util.UUID;

public record CompleteReservationCommand(
    UUID tenantId,
    UUID storeId,
    UUID reservationId,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    Instant completedAt,
    String reasonCode,
    String note
) {
}
