package com.rpb.reservation.reservation.application;

import java.util.UUID;

public record AssignableReservationTable(
    UUID tableId,
    String tableCode,
    String displayName,
    String areaName,
    int capacityMin,
    int capacityMax
) {
}
