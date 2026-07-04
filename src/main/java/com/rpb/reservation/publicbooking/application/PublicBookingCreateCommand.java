package com.rpb.reservation.publicbooking.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PublicBookingCreateCommand(
    UUID storeId,
    Integer partySize,
    Instant reservedStartAt,
    LocalDate businessDate,
    String phoneE164,
    String note,
    String idempotencyKey,
    String customerSessionToken
) {
}
