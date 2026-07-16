package com.rpb.reservation.table.api;

import java.util.UUID;

public record TableSwitchRequest(
    UUID tableId,
    UUID tableGroupId,
    String reasonCode,
    String note
) {
}
