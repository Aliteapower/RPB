package com.rpb.reservation.queue.application.command;

import java.time.Instant;
import java.util.UUID;

public record CancelQueueTicketCommand(
    UUID tenantId,
    UUID storeId,
    UUID queueTicketId,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    Instant cancelledAt,
    String reasonCode,
    String note
) {
}
