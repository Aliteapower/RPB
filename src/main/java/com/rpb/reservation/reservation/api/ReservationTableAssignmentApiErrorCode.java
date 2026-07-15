package com.rpb.reservation.reservation.api;

import org.springframework.http.HttpStatus;

public enum ReservationTableAssignmentApiErrorCode {
    INVALID_COMMAND(HttpStatus.BAD_REQUEST, "reservation.table_assignment.invalid_command"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "reservation.table_assignment.missing_idempotency_key"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "reservation.table_assignment.forbidden"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "reservation.table_assignment.store_scope_mismatch"),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "reservation.table_assignment.store_not_found"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "reservation.table_assignment.reservation_not_found"),
    TABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "reservation.table_assignment.table_not_found"),
    RESERVATION_NOT_ASSIGNABLE(HttpStatus.CONFLICT, "reservation.table_assignment.reservation_not_assignable"),
    RESERVATION_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "reservation.table_assignment.reservation_already_assigned"),
    TABLE_CAPACITY_INSUFFICIENT(HttpStatus.CONFLICT, "reservation.table_assignment.table_capacity_insufficient"),
    TABLE_NOT_AVAILABLE(HttpStatus.CONFLICT, "reservation.table_assignment.table_not_available"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "reservation.table_assignment.idempotency_conflict"),
    COMMAND_IN_PROGRESS(HttpStatus.CONFLICT, "reservation.table_assignment.command_in_progress"),
    FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY(HttpStatus.CONFLICT, "reservation.table_assignment.failed_idempotency_requires_new_key"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "reservation.table_assignment.persistence_error"),
    BUSINESS_EVENT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "reservation.table_assignment.business_event_write_failed"),
    AUDIT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "reservation.table_assignment.audit_write_failed");

    private final HttpStatus httpStatus;
    private final String messageKey;

    ReservationTableAssignmentApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
