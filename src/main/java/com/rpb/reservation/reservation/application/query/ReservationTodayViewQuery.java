package com.rpb.reservation.reservation.application.query;

import java.util.UUID;

public record ReservationTodayViewQuery(
    UUID tenantId,
    UUID storeId,
    UUID actorId,
    String actorType,
    String businessDate,
    String status
) {
}
