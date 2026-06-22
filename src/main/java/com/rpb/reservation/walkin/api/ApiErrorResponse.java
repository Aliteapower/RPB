package com.rpb.reservation.walkin.api;

import java.util.Map;

public record ApiErrorResponse(
    boolean success,
    ApiErrorBody error,
    ApiIdempotencyResponse idempotency
) {

    public static ApiErrorResponse of(ApiErrorCode code) {
        return of(code, ApiIdempotencyResponse.failed());
    }

    public static ApiErrorResponse of(ApiErrorCode code, ApiIdempotencyResponse idempotency) {
        return new ApiErrorResponse(
            false,
            new ApiErrorBody(code.name(), code.messageKey(), Map.of()),
            idempotency
        );
    }

    public record ApiErrorBody(
        String code,
        String messageKey,
        Map<String, Object> details
    ) {
    }
}
