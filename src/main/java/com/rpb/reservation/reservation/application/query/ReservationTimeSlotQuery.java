package com.rpb.reservation.reservation.application.query;

import java.util.UUID;

public record ReservationTimeSlotQuery(
    UUID tenantId,
    UUID storeId,
    UUID actorId,
    String actorType,
    String businessDate
) {
}
