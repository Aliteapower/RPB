package com.rpb.reservation.reservation.api;

import org.springframework.http.HttpStatus;

public enum ReservationMealPeriodApiErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "reservation.meal_period.unauthenticated"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "reservation.meal_period.forbidden"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "reservation.meal_period.request_invalid"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "reservation.meal_period.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    ReservationMealPeriodApiErrorCode(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String messageKey() {
        return messageKey;
    }
}
