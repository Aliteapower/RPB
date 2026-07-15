package com.rpb.reservation.reservation.application.query;

import java.util.UUID;

public record AssignableReservationTablesQuery(
    UUID tenantId,
    UUID storeId,
    UUID reservationId,
    UUID actorId,
    String actorType
) {
}
