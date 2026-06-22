package com.rpb.reservation.reservation.api;

import java.util.Map;

public record ReservationTodayViewApiErrorResponse(
    boolean success,
    ErrorBody error
) {
    public static ReservationTodayViewApiErrorResponse of(ReservationApiErrorCode code) {
        return new ReservationTodayViewApiErrorResponse(
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
