package com.rpb.reservation.cleaning.application.command;

import java.util.UUID;

public record StartCleaningCommand(
    UUID tenantId,
    UUID storeId,
    UUID seatingId,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    String reasonCode,
    String note
) {
}
