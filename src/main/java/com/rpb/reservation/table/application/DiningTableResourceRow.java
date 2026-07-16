package com.rpb.reservation.table.application;

import java.util.UUID;

public record DiningTableResourceRow(
    UUID resourceId,
    String code,
    String displayName,
    String areaName,
    int capacityMin,
    int capacityMax,
    String status
) {
}
