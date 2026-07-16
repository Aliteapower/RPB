package com.rpb.reservation.reservation.api;

import org.springframework.http.HttpStatus;

public enum ReservationPublicShareApiErrorCode {
    INVALID_TOKEN(HttpStatus.BAD_REQUEST, "reservation.public_share.invalid_token"),
    TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "reservation.public_share.token_not_found"),
    TOKEN_REVOKED(HttpStatus.GONE, "reservation.public_share.token_revoked"),
    TOKEN_EXPIRED(HttpStatus.GONE, "reservation.public_share.token_expired"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "reservation.public_share.reservation_not_found"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "reservation.public_share.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    ReservationPublicShareApiErrorCode(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    HttpStatus httpStatus() {
        return httpStatus;
    }

    String messageKey() {
        return messageKey;
    }
}
