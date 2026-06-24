package com.rpb.reservation.walkin.application.command;

import java.util.UUID;

public record QueueWalkInCommand(
    UUID tenantId,
    UUID storeId,
    Integer partySize,
    UUID customerId,
    String customerName,
    String customerNickname,
    String phoneE164,
    String note,
    String idempotencyKey,
    UUID actorId,
    String actorType
) {
}
