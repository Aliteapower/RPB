package com.rpb.reservation.reservation.api;

import java.util.Map;

public record ReservationMealPeriodApiErrorResponse(
    boolean success,
    ErrorBody error
) {
    public static ReservationMealPeriodApiErrorResponse of(ReservationMealPeriodApiErrorCode code) {
        return new ReservationMealPeriodApiErrorResponse(
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
