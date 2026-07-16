package com.rpb.reservation.platform.api;

import org.springframework.http.HttpStatus;

public enum PlatformProfileApiErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "platform.profile.unauthenticated"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "platform.profile.forbidden"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "platform.profile.request_invalid"),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "platform.profile.not_found"),
    SOCIAL_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "platform.profile.social_link_not_found"),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "platform.profile.media_not_found"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "platform.profile.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    PlatformProfileApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
