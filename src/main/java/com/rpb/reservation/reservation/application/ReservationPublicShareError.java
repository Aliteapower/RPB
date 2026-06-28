package com.rpb.reservation.reservation.application;

public enum ReservationPublicShareError {
    INVALID_TOKEN,
    TOKEN_NOT_FOUND,
    TOKEN_REVOKED,
    TOKEN_EXPIRED,
    RESERVATION_NOT_FOUND,
    PERSISTENCE_ERROR
}
