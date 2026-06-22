package com.rpb.reservation.cleaning.api;

import org.springframework.http.HttpStatus;

public enum CleaningApiErrorCode {
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "cleaning.store_not_found"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "cleaning.store_scope_mismatch"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "cleaning.forbidden"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "cleaning.missing_idempotency_key"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "cleaning.idempotency_conflict"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "cleaning.idempotency_in_progress"),
    IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY(HttpStatus.CONFLICT, "cleaning.idempotency_failed_requires_new_key"),
    SEATING_NOT_FOUND(HttpStatus.NOT_FOUND, "cleaning.seating_not_found"),
    SEATING_RESOURCE_NOT_FOUND(HttpStatus.CONFLICT, "cleaning.seating_resource_not_found"),
    CLEANING_NOT_FOUND(HttpStatus.NOT_FOUND, "cleaning.not_found"),
    CLEANING_ALREADY_ACTIVE(HttpStatus.CONFLICT, "cleaning.already_active"),
    CLEANING_ALREADY_COMPLETED(HttpStatus.CONFLICT, "cleaning.already_completed"),
    CLEANING_TARGET_INVALID(HttpStatus.CONFLICT, "cleaning.target_invalid"),
    TABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "cleaning.table_not_found"),
    TABLE_GROUP_INVALID(HttpStatus.CONFLICT, "cleaning.table_group_invalid"),
    TABLE_NOT_OCCUPIED(HttpStatus.CONFLICT, "cleaning.table_not_occupied"),
    TABLE_NOT_CLEANING(HttpStatus.CONFLICT, "cleaning.table_not_cleaning"),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT, "cleaning.illegal_state_transition"),
    AUDIT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "cleaning.audit_write_failed"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "cleaning.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    CleaningApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
