package com.rpb.reservation.reservation.application;

import java.util.Arrays;

public enum ReservationCompleteError {
    INVALID_COMMAND("invalid_command"),
    MISSING_IDEMPOTENCY_KEY("missing_idempotency_key"),
    STORE_NOT_FOUND("store_not_found"),
    STORE_SCOPE_MISMATCH("store_scope_mismatch"),
    STORE_ACCESS_DENIED("store_access_denied"),
    RESERVATION_NOT_FOUND("reservation_not_found"),
    RESERVATION_CANNOT_COMPLETE_DRAFT("reservation_cannot_complete_draft"),
    RESERVATION_CANNOT_COMPLETE_CONFIRMED("reservation_cannot_complete_confirmed"),
    RESERVATION_CANNOT_COMPLETE_ARRIVED("reservation_cannot_complete_arrived"),
    RESERVATION_CANNOT_COMPLETE_CANCELLED("reservation_cannot_complete_cancelled"),
    RESERVATION_CANNOT_COMPLETE_NO_SHOW("reservation_cannot_complete_no_show"),
    RESERVATION_COMPLETED_WITHOUT_ACTIVE_SEATING("reservation_completed_without_active_seating"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    COMMAND_IN_PROGRESS("command_in_progress"),
    FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY("failed_idempotency_requires_new_key"),
    ILLEGAL_STATE_TRANSITION("illegal_state_transition"),
    BUSINESS_EVENT_WRITE_FAILED("business_event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED("state_transition_write_failed"),
    AUDIT_WRITE_FAILED("audit_write_failed"),
    REPOSITORY_SAVE_FAILED("repository_save_failed"),
    PERSISTENCE_ERROR("persistence_error");

    private final String code;

    ReservationCompleteError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ReservationCompleteError fromCode(String code) {
        return Arrays.stream(values())
            .filter(error -> error.code.equals(code))
            .findFirst()
            .orElse(INVALID_COMMAND);
    }
}
