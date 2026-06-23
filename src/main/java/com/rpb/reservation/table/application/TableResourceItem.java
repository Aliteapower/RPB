package com.rpb.reservation.table.application;

import java.util.List;
import java.util.UUID;

public record TableResourceItem(
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

    public TableResourceItem {
        memberTableCodes = memberTableCodes == null ? List.of() : List.copyOf(memberTableCodes);
    }
}
