package com.rpb.reservation.reservation.api;

import java.util.List;
import java.util.UUID;

public record AssignableReservationTablesResponse(
    boolean success,
    UUID reservationId,
    int partySize,
    List<AssignableReservationTableResponse> tables
) {
    public record AssignableReservationTableResponse(
        UUID tableId,
        String tableCode,
        String displayName,
        String areaName,
        int capacityMin,
        int capacityMax
    ) {
    }
}
