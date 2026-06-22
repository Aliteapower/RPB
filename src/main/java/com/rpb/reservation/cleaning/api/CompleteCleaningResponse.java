package com.rpb.reservation.cleaning.api;

import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.util.List;
import java.util.UUID;

public record CompleteCleaningResponse(
    boolean success,
    UUID cleaningId,
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
