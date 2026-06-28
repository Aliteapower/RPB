package com.rpb.reservation.reservation.application.port.out;

import java.time.Instant;
import java.util.UUID;

public record ReservationPublicShareRow(
    String token,
    String status,
    Instant expiresAt,
    UUID reservationId,
    String reservationNo,
    int partySize,
    Instant reservedStartAt,
    String tableCode,
    String storeDisplayName,
    String storeTimezone,
    String shareDisplayName,
    String shareAddress,
    String googleMapUrl,
    String shareContactPhone,
    String reservationShareNote
) {
}
