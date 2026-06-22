package com.rpb.reservation.queue.application.command;

import java.time.Instant;
import java.util.UUID;

public record CallQueueTicketCommand(
    UUID tenantId,
    UUID storeId,
    UUID queueTicketId,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    Instant calledAt,
    String reasonCode,
    String note
) {
}
