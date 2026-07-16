package com.rpb.reservation.queue.application.command;

import java.util.List;
import java.util.UUID;

public record SeatCalledQueueTicketCommand(
    UUID tenantId,
    UUID storeId,
    UUID queueTicketId,
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

    public SeatCalledQueueTicketCommand {
        temporaryTableIds = temporaryTableIds == null ? List.of() : List.copyOf(temporaryTableIds);
    }
}
