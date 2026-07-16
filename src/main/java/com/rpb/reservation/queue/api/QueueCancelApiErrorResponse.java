package com.rpb.reservation.queue.api;

import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.util.Map;

public record QueueCancelApiErrorResponse(
    boolean success,
    ErrorBody error,
    ApiIdempotencyResponse idempotency
) {

    public static QueueCancelApiErrorResponse of(QueueCancelApiErrorCode code, ApiIdempotencyResponse idempotency) {
        return new QueueCancelApiErrorResponse(
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
