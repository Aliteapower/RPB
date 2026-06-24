package com.rpb.reservation.table.application.command;

import java.util.UUID;

public record SwitchTableCommand(
    UUID tenantId,
    UUID storeId,
    UUID seatingId,
    UUID tableId,
    UUID tableGroupId,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    String reasonCode,
    String note
) {
}
