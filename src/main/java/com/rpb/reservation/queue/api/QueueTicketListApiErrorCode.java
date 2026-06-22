package com.rpb.reservation.queue.api;

import org.springframework.http.HttpStatus;

public enum QueueTicketListApiErrorCode {
    INVALID_QUERY(HttpStatus.BAD_REQUEST, "queue.list.invalid_query"),
    INVALID_STATUS(HttpStatus.BAD_REQUEST, "queue.list.invalid_status"),
    INVALID_LIMIT(HttpStatus.BAD_REQUEST, "queue.list.invalid_limit"),
    INVALID_OFFSET(HttpStatus.BAD_REQUEST, "queue.list.invalid_offset"),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.list.store_not_found"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "queue.list.store_scope_mismatch"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "queue.list.forbidden"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "queue.list.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    QueueTicketListApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
