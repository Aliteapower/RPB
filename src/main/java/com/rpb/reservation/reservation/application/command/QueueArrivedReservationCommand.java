package com.rpb.reservation.reservation.application.command;

import java.util.UUID;

public record QueueArrivedReservationCommand(
    UUID tenantId,
    UUID storeId,
    UUID reservationId,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    String partySizeGroup,
    String reasonCode,
    String note
) {
}
