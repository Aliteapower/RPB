package com.rpb.reservation.reservation.api;

import java.util.Map;

public record ReservationPublicShareApiErrorResponse(
    boolean success,
    ErrorBody error
) {
    static ReservationPublicShareApiErrorResponse of(ReservationPublicShareApiErrorCode code) {
        return new ReservationPublicShareApiErrorResponse(
            false,
            new ErrorBody(code.name(), code.messageKey(), Map.of())
        );
    }

    public record ErrorBody(
        String code,
        String messageKey,
        Map<String, Object> details
    ) {
    }
}
