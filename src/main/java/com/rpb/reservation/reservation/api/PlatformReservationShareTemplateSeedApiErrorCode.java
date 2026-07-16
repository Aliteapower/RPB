package com.rpb.reservation.reservation.api;

import org.springframework.http.HttpStatus;

public enum PlatformReservationShareTemplateSeedApiErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "platform.reservation_share_template_seed.unauthenticated"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "platform.reservation_share_template_seed.forbidden"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "platform.reservation_share_template_seed.request_invalid"),
    SEED_NOT_FOUND(HttpStatus.NOT_FOUND, "platform.reservation_share_template_seed.not_found"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "platform.reservation_share_template_seed.version_conflict"),
    TEMPLATE_UNKNOWN_VARIABLE(HttpStatus.BAD_REQUEST, "platform.reservation_share_template_seed.unknown_variable"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "platform.reservation_share_template_seed.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    PlatformReservationShareTemplateSeedApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
