package com.rpb.reservation.table.api;

import java.util.List;
import java.util.UUID;

public record SaveTemporaryTableGroupRequest(
    String groupName,
    List<UUID> tableIds,
    String businessDate
) {

    public SaveTemporaryTableGroupRequest {
        tableIds = tableIds == null ? List.of() : List.copyOf(tableIds);
    }
}
