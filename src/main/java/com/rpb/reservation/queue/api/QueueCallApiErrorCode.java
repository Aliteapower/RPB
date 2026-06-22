package com.rpb.reservation.queue.api;

import org.springframework.http.HttpStatus;

public enum QueueCallApiErrorCode {
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.call.store_not_found"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "queue.call.store_scope_mismatch"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "queue.call.forbidden"),
    INVALID_COMMAND(HttpStatus.BAD_REQUEST, "queue.call.invalid_command"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "queue.call.missing_idempotency_key"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "queue.call.idempotency_conflict"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "queue.call.idempotency_in_progress"),
    IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY(HttpStatus.CONFLICT, "queue.call.idempotency_failed_requires_new_key"),
    QUEUE_TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.call.queue_ticket_not_found"),
    QUEUE_TICKET_STATUS_NOT_WAITING(HttpStatus.CONFLICT, "queue.call.queue_ticket_status_not_waiting"),
    QUEUE_CALL_EVIDENCE_INCOMPLETE(HttpStatus.CONFLICT, "queue.call.queue_call_evidence_incomplete"),
    QUEUE_TICKET_CANNOT_CALL_SEATED(HttpStatus.CONFLICT, "queue.call.queue_ticket_cannot_call_seated"),
    QUEUE_TICKET_CANNOT_CALL_CANCELLED(HttpStatus.CONFLICT, "queue.call.queue_ticket_cannot_call_cancelled"),
    QUEUE_TICKET_CANNOT_CALL_EXPIRED(HttpStatus.CONFLICT, "queue.call.queue_ticket_cannot_call_expired"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.call.reservation_not_found"),
    RESERVATION_STATUS_NOT_ARRIVED(HttpStatus.CONFLICT, "queue.call.reservation_status_not_arrived"),
    QUEUE_CALL_HOLD_POLICY_INVALID(HttpStatus.CONFLICT, "queue.call.queue_call_hold_policy_invalid"),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT, "queue.call.illegal_state_transition"),
    AUDIT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.call.audit_write_failed"),
    EVENT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.call.event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.call.state_transition_write_failed"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "queue.call.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    QueueCallApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
