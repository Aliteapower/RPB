package com.rpb.reservation.table.api;

import java.util.List;
import java.util.UUID;

public record TemporaryTableGroupResponse(
    boolean success,
    UUID tableGroupId,
    String groupName,
    String groupType,
    String status,
    int capacityMin,
    int capacityMax,
    List<UUID> tableIds
) {

    public TemporaryTableGroupResponse {
        tableIds = tableIds == null ? List.of() : List.copyOf(tableIds);
    }
}
