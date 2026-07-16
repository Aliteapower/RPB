package com.rpb.reservation.reservation.application;

import java.util.Arrays;

public enum ReservationTableAssignmentError {
    INVALID_COMMAND("invalid_command"),
    MISSING_IDEMPOTENCY_KEY("missing_idempotency_key"),
    FORBIDDEN("forbidden"),
    STORE_NOT_FOUND("store_not_found"),
    STORE_SCOPE_MISMATCH("store_scope_mismatch"),
    RESERVATION_NOT_FOUND("reservation_not_found"),
    TABLE_NOT_FOUND("table_not_found"),
    RESERVATION_NOT_ASSIGNABLE("reservation_not_assignable"),
    RESERVATION_ALREADY_ASSIGNED("reservation_already_assigned"),
    TABLE_CAPACITY_INSUFFICIENT("table_capacity_insufficient"),
    TABLE_NOT_AVAILABLE("table_not_available"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    COMMAND_IN_PROGRESS("command_in_progress"),
    FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY("failed_idempotency_requires_new_key"),
    PERSISTENCE_ERROR("persistence_error"),
    BUSINESS_EVENT_WRITE_FAILED("business_event_write_failed"),
    AUDIT_WRITE_FAILED("audit_write_failed");

    private final String code;

    ReservationTableAssignmentError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ReservationTableAssignmentError fromCode(String code) {
        return Arrays.stream(values())
            .filter(error -> error.code.equals(code))
            .findFirst()
            .orElse(INVALID_COMMAND);
    }
}
