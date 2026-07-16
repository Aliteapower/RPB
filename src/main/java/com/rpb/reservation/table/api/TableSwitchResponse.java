package com.rpb.reservation.table.api;

import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.util.List;
import java.util.UUID;

public record TableSwitchResponse(
    boolean success,
    UUID seatingId,
    ResourceResponse fromResource,
    ResourceResponse toResource,
    UUID cleaningId,
    String seatingStatus,
    List<String> events,
    ApiIdempotencyResponse idempotency
) {

    public record ResourceResponse(
        String type,
        UUID id,
        String status
    ) {
    }
}
