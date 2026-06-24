package com.rpb.reservation.reservation.application.command;

import java.util.List;
import java.util.UUID;

public record SeatArrivedReservationCommand(
    UUID tenantId,
    UUID storeId,
    UUID reservationId,
    UUID tableId,
    UUID tableGroupId,
    List<UUID> temporaryTableIds,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    String overrideReasonCode,
    String overrideNote,
    String note
) {

    public SeatArrivedReservationCommand {
        temporaryTableIds = temporaryTableIds == null ? List.of() : List.copyOf(temporaryTableIds);
    }
}
