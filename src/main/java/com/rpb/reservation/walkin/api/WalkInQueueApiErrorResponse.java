package com.rpb.reservation.walkin.api;

import java.util.Map;

public record WalkInQueueApiErrorResponse(
    boolean success,
    ErrorBody error,
    ApiIdempotencyResponse idempotency
) {

    public static WalkInQueueApiErrorResponse of(WalkInQueueApiErrorCode code, ApiIdempotencyResponse idempotency) {
        return new WalkInQueueApiErrorResponse(
            false,
            new ErrorBody(code.name(), code.messageKey(), Map.of()),
            idempotency
        );
    }

    public record ErrorBody(
        String code,
        String messageKey,
        Map<String, Object> details
    ) {
    }
}
