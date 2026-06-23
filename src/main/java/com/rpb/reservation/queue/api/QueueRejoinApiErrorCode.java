package com.rpb.reservation.queue.api;

import org.springframework.http.HttpStatus;

public enum QueueRejoinApiErrorCode {
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.rejoin.store_not_found"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "queue.rejoin.store_scope_mismatch"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "queue.rejoin.forbidden"),
    INVALID_COMMAND(HttpStatus.BAD_REQUEST, "queue.rejoin.invalid_command"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "queue.rejoin.missing_idempotency_key"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "queue.rejoin.idempotency_conflict"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "queue.rejoin.idempotency_in_progress"),
    IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY(HttpStatus.CONFLICT, "queue.rejoin.idempotency_failed_requires_new_key"),
    QUEUE_TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.rejoin.queue_ticket_not_found"),
    QUEUE_TICKET_STATUS_NOT_SKIPPED(HttpStatus.CONFLICT, "queue.rejoin.queue_ticket_status_not_skipped"),
    QUEUE_REJOIN_EVIDENCE_INCOMPLETE(HttpStatus.CONFLICT, "queue.rejoin.queue_rejoin_evidence_incomplete"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.rejoin.reservation_not_found"),
    RESERVATION_STATUS_NOT_ARRIVED(HttpStatus.CONFLICT, "queue.rejoin.reservation_status_not_arrived"),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT, "queue.rejoin.illegal_state_transition"),
    AUDIT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.rejoin.audit_write_failed"),
    EVENT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.rejoin.event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.rejoin.state_transition_write_failed"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "queue.rejoin.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    QueueRejoinApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
