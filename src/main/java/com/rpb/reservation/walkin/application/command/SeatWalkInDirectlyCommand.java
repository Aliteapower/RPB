package com.rpb.reservation.walkin.application.command;

import java.util.List;
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
    List<UUID> temporaryTableIds,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    String overrideReasonCode,
    String overrideNote
) {

    public SeatWalkInDirectlyCommand {
        temporaryTableIds = temporaryTableIds == null ? List.of() : List.copyOf(temporaryTableIds);
    }
}
