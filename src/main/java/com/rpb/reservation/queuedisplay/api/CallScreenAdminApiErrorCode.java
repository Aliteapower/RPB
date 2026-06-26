package com.rpb.reservation.queuedisplay.api;

import org.springframework.http.HttpStatus;

public enum CallScreenAdminApiErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "call_screen.admin.unauthenticated"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "call_screen.admin.forbidden"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "call_screen.admin.store_scope_mismatch"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "call_screen.admin.request_invalid"),
    AD_SET_NOT_FOUND(HttpStatus.NOT_FOUND, "call_screen.admin.ad_set_not_found"),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "call_screen.admin.media_not_found"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "call_screen.admin.version_conflict"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "call_screen.admin.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    CallScreenAdminApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
