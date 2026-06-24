package com.rpb.reservation.table.api;

import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.util.Map;

public record TableSwitchApiErrorResponse(
    boolean success,
    ErrorBody error,
    ApiIdempotencyResponse idempotency
) {

    public static TableSwitchApiErrorResponse of(TableSwitchApiErrorCode code, ApiIdempotencyResponse idempotency) {
        return new TableSwitchApiErrorResponse(
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
