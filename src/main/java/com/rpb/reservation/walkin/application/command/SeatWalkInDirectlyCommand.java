package com.rpb.reservation.walkin.application.command;

import java.util.UUID;

public record SeatWalkInDirectlyCommand(
    UUID tenantId,
    UUID storeId,
    Integer partySize,
    UUID customerId,
    String customerName,
    String customerNickname,
    String phoneE164,
    UUID tableId,
    UUID tableGroupId,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    String overrideReasonCode,
    String overrideNote
) {
}
