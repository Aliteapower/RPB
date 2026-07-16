package com.rpb.reservation.reservation.application;

import java.util.List;
import java.util.UUID;

public record AssignableReservationTablesResult(
    boolean success,
    ReservationTableAssignmentError error,
    UUID reservationId,
    Integer partySize,
    List<AssignableReservationTable> tables
) {
    public AssignableReservationTablesResult {
        tables = tables == null ? List.of() : List.copyOf(tables);
    }

    public static AssignableReservationTablesResult success(
        UUID reservationId,
        int partySize,
        List<AssignableReservationTable> tables
    ) {
        return new AssignableReservationTablesResult(true, null, reservationId, partySize, tables);
    }

    public static AssignableReservationTablesResult failure(ReservationTableAssignmentError error) {
        return new AssignableReservationTablesResult(false, error, null, null, List.of());
    }
}
