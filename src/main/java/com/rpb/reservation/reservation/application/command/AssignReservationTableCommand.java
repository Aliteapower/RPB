package com.rpb.reservation.reservation.application.command;

import java.util.UUID;

public record AssignReservationTableCommand(
    UUID tenantId,
    UUID storeId,
    UUID reservationId,
    UUID tableId,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    String source
) {
}
