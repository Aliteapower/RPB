package com.rpb.reservation.queue.api;

import org.springframework.http.HttpStatus;

public enum QueueCancelApiErrorCode {
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.cancel.store_not_found"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "queue.cancel.store_scope_mismatch"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "queue.cancel.forbidden"),
    INVALID_COMMAND(HttpStatus.BAD_REQUEST, "queue.cancel.invalid_command"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "queue.cancel.missing_idempotency_key"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "queue.cancel.idempotency_conflict"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "queue.cancel.idempotency_in_progress"),
    IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY(HttpStatus.CONFLICT, "queue.cancel.idempotency_failed_requires_new_key"),
    QUEUE_TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.cancel.queue_ticket_not_found"),
    QUEUE_TICKET_CANNOT_CANCEL_SEATED(HttpStatus.CONFLICT, "queue.cancel.queue_ticket_cannot_cancel_seated"),
    QUEUE_TICKET_CANNOT_CANCEL_EXPIRED(HttpStatus.CONFLICT, "queue.cancel.queue_ticket_cannot_cancel_expired"),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT, "queue.cancel.illegal_state_transition"),
    AUDIT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.cancel.audit_write_failed"),
    EVENT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.cancel.event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.cancel.state_transition_write_failed"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "queue.cancel.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    QueueCancelApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
