package com.rpb.reservation.reservation.application.command;

import java.util.UUID;

public record ReservationShareIntentCommand(
    UUID tenantId,
    UUID storeId,
    UUID reservationId,
    UUID actorId,
    String actorType,
    String publicShareBaseUrl,
    String channel
) {
}
