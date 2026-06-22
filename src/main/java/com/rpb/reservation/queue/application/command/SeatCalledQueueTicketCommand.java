package com.rpb.reservation.queue.application.command;

import java.util.UUID;

public record SeatCalledQueueTicketCommand(
    UUID tenantId,
    UUID storeId,
    UUID queueTicketId,
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
