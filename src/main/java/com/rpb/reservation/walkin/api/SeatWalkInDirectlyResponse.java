package com.rpb.reservation.walkin.api;

import java.util.List;
import java.util.UUID;

public record SeatWalkInDirectlyResponse(
    boolean success,
    UUID walkInId,
    UUID seatingId,
    ResourceResponse resource,
    int partySize,
    String status,
    List<String> events,
    ApiIdempotencyResponse idempotency
) {

    public record ResourceResponse(
        String type,
        UUID id,
        String label
    ) {
    }
}
