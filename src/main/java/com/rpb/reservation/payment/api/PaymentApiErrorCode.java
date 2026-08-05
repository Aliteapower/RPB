package com.rpb.reservation.payment.api;

import org.springframework.http.HttpStatus;

public enum PaymentApiErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST),
    PAYMENT_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND),
    PAYMENT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND),
    PAYMENT_PROFILE_DISABLED(HttpStatus.CONFLICT),
    VERSION_CONFLICT(HttpStatus.CONFLICT),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    PaymentApiErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
