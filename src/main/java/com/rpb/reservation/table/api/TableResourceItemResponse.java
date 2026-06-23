package com.rpb.reservation.table.api;

import java.util.List;
import java.util.UUID;

public record TableResourceItemResponse(
    String resourceType,
    UUID resourceId,
    String code,
    String displayName,
    String areaName,
    int capacityMin,
    int capacityMax,
    String status,
    boolean selectable,
    String selectionDisabledReason,
    List<String> memberTableCodes
) {

    public TableResourceItemResponse {
        memberTableCodes = memberTableCodes == null ? List.of() : List.copyOf(memberTableCodes);
    }
}
