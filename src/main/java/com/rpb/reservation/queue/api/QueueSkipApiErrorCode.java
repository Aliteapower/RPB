package com.rpb.reservation.queue.api;

import org.springframework.http.HttpStatus;

public enum QueueSkipApiErrorCode {
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.skip.store_not_found"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "queue.skip.store_scope_mismatch"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "queue.skip.forbidden"),
    INVALID_COMMAND(HttpStatus.BAD_REQUEST, "queue.skip.invalid_command"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "queue.skip.missing_idempotency_key"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "queue.skip.idempotency_conflict"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "queue.skip.idempotency_in_progress"),
    IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY(HttpStatus.CONFLICT, "queue.skip.idempotency_failed_requires_new_key"),
    QUEUE_TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.skip.queue_ticket_not_found"),
    QUEUE_TICKET_STATUS_NOT_CALLED(HttpStatus.CONFLICT, "queue.skip.queue_ticket_status_not_called"),
    QUEUE_SKIP_EVIDENCE_INCOMPLETE(HttpStatus.CONFLICT, "queue.skip.queue_skip_evidence_incomplete"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.skip.reservation_not_found"),
    RESERVATION_STATUS_NOT_ARRIVED(HttpStatus.CONFLICT, "queue.skip.reservation_status_not_arrived"),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT, "queue.skip.illegal_state_transition"),
    AUDIT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.skip.audit_write_failed"),
    EVENT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.skip.event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.skip.state_transition_write_failed"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "queue.skip.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    QueueSkipApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
