package com.rpb.reservation.queuedisplay.api;

import org.springframework.http.HttpStatus;

public enum PlatformCallScreenSeedApiErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "platform.call_screen_seed.unauthenticated"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "platform.call_screen_seed.forbidden"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "platform.call_screen_seed.request_invalid"),
    SEED_NOT_FOUND(HttpStatus.NOT_FOUND, "platform.call_screen_seed.not_found"),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "platform.call_screen_seed.media_not_found"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "platform.call_screen_seed.version_conflict"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "platform.call_screen_seed.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    PlatformCallScreenSeedApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
