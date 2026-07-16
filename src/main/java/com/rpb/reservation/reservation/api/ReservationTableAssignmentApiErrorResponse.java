package com.rpb.reservation.reservation.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationTableAssignmentApiErrorResponse(
    boolean success,
    ErrorBody error,
    ApiIdempotencyResponse idempotency
) {
    public static ReservationTableAssignmentApiErrorResponse of(
        ReservationTableAssignmentApiErrorCode code,
        ApiIdempotencyResponse idempotency
    ) {
        return new ReservationTableAssignmentApiErrorResponse(
            false,
            new ErrorBody(code.name(), code.messageKey(), Map.of()),
            idempotency
        );
    }

    public record ErrorBody(String code, String messageKey, Map<String, Object> details) {
    }
}
