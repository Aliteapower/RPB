package com.rpb.reservation.walkin.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiIdempotencyResponse(
    String status,
    Boolean replayed
) {

    public static ApiIdempotencyResponse completed(boolean replayed) {
        return new ApiIdempotencyResponse("completed", replayed);
    }

    public static ApiIdempotencyResponse failed() {
        return new ApiIdempotencyResponse("failed", null);
    }

    public static ApiIdempotencyResponse started() {
        return new ApiIdempotencyResponse("started", null);
    }

    public static ApiIdempotencyResponse conflict() {
        return new ApiIdempotencyResponse("conflict", null);
    }
}
