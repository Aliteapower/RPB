package com.rpb.reservation.reservation.application.command;

import java.util.UUID;

public record SeatArrivedReservationCommand(
    UUID tenantId,
    UUID storeId,
    UUID reservationId,
    UUID tableId,
    UUID tableGroupId,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    String overrideReasonCode,
    String overrideNote,
    String note
) {
}
