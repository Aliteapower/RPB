package com.rpb.reservation.walkin.api;

import org.springframework.http.HttpStatus;

public enum WalkInQueueApiErrorCode {
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "walkin.queue.store_not_found"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "walkin.queue.store_scope_mismatch"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "walkin.queue.forbidden"),
    INVALID_COMMAND(HttpStatus.BAD_REQUEST, "walkin.queue.invalid_command"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "walkin.queue.missing_idempotency_key"),
    INVALID_PARTY_SIZE(HttpStatus.BAD_REQUEST, "walkin.queue.invalid_party_size"),
    INVALID_CUSTOMER_IDENTITY(HttpStatus.BAD_REQUEST, "walkin.queue.invalid_customer_identity"),
    QUEUE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "walkin.queue.queue_group_not_found"),
    QUEUE_GROUP_PARTY_SIZE_MISMATCH(HttpStatus.CONFLICT, "walkin.queue.queue_group_party_size_mismatch"),
    QUEUE_TICKET_NUMBER_CONFLICT(HttpStatus.CONFLICT, "walkin.queue.queue_ticket_number_conflict"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "walkin.queue.idempotency_conflict"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "walkin.queue.idempotency_in_progress"),
    IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY(HttpStatus.CONFLICT, "walkin.queue.idempotency_failed_requires_new_key"),
    AUDIT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "walkin.queue.audit_write_failed"),
    EVENT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "walkin.queue.event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "walkin.queue.state_transition_write_failed"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "walkin.queue.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    WalkInQueueApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
