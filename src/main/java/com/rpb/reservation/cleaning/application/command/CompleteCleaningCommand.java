package com.rpb.reservation.cleaning.application.command;

import java.util.UUID;

public record CompleteCleaningCommand(
    UUID tenantId,
    UUID storeId,
    UUID cleaningId,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    String reasonCode,
    String note
) {
}
