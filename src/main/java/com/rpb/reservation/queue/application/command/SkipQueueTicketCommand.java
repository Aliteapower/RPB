package com.rpb.reservation.queue.application.command;

import java.time.Instant;
import java.util.UUID;

public record SkipQueueTicketCommand(
    UUID tenantId,
    UUID storeId,
    UUID queueTicketId,
    Instant skippedAt,
    String reasonCode,
    String note,
    String idempotencyKey,
    UUID actorId,
    String actorType
) {
}
