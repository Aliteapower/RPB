package com.rpb.reservation.cleaning.api;

import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.util.List;
import java.util.UUID;

public record StartCleaningResponse(
    boolean success,
    UUID cleaningId,
    UUID seatingId,
    ResourceResponse resource,
    String cleaningStatus,
    String tableStatus,
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
