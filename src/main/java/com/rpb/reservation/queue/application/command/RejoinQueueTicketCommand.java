package com.rpb.reservation.queue.application.command;

import java.util.UUID;

public record RejoinQueueTicketCommand(
    UUID tenantId,
    UUID storeId,
    UUID queueTicketId,
    String note,
    String idempotencyKey,
    UUID actorId,
    String actorType
) {
}
