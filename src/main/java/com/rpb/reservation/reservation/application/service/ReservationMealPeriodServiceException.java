package com.rpb.reservation.reservation.application.service;

public class ReservationMealPeriodServiceException extends RuntimeException {
    private final ReservationMealPeriodServiceErrorCode code;

    public ReservationMealPeriodServiceException(ReservationMealPeriodServiceErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public ReservationMealPeriodServiceErrorCode code() {
        return code;
    }
}
