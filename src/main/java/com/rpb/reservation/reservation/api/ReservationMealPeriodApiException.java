package com.rpb.reservation.reservation.api;

public class ReservationMealPeriodApiException extends RuntimeException {
    private final ReservationMealPeriodApiErrorCode code;

    public ReservationMealPeriodApiException(ReservationMealPeriodApiErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public ReservationMealPeriodApiErrorCode code() {
        return code;
    }
}
