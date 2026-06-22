package com.rpb.reservation.reservation.api;

import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.util.Map;

public record ReservationApiErrorResponse(
    boolean success,
    ReservationApiErrorBody error,
    ApiIdempotencyResponse idempotency
) {

    public static ReservationApiErrorResponse of(ReservationApiErrorCode code, ApiIdempotencyResponse idempotency) {
        return new ReservationApiErrorResponse(
            false,
            new ReservationApiErrorBody(code.name(), code.messageKey(), Map.of()),
            idempotency
        );
    }

    public record ReservationApiErrorBody(
        String code,
        String messageKey,
        Map<String, Object> details
    ) {
    }
}
