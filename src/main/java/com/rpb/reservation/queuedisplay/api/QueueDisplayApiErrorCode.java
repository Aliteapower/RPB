package com.rpb.reservation.queuedisplay.api;

import org.springframework.http.HttpStatus;

public enum QueueDisplayApiErrorCode {
    FORBIDDEN(HttpStatus.FORBIDDEN, "queue.display.forbidden"),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.display.store_not_found"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "queue.display.store_scope_mismatch"),
    QUEUE_DISPLAY_CONFIG_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "queue.display.config_invalid"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "queue.display.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    QueueDisplayApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
