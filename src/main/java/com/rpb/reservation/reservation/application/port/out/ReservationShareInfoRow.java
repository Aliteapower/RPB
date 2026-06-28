package com.rpb.reservation.reservation.application.port.out;

import java.time.Instant;
import java.util.UUID;

public record ReservationShareInfoRow(
    UUID reservationId,
    String reservationNo,
    int partySize,
    Instant reservedStartAt,
    Instant holdUntilAt,
    String tableCode,
    String customerName,
    String customerNickname,
    String customerPhoneE164,
    String storeDisplayName,
    String storeTimezone,
    String shareDisplayName,
    String shareAddress,
    String googleMapUrl,
    String shareContactPhone,
    String reservationShareNote,
    String reservationShareTemplate
) {
}
