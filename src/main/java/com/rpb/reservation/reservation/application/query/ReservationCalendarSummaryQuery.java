package com.rpb.reservation.reservation.application.query;

import java.util.UUID;

public record ReservationCalendarSummaryQuery(
    UUID tenantId,
    UUID storeId,
    UUID actorId,
    String actorType,
    String month
) {
}
