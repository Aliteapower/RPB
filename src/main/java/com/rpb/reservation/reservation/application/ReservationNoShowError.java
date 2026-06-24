package com.rpb.reservation.reservation.application;

import java.util.Arrays;

public enum ReservationNoShowError {
    INVALID_COMMAND("invalid_command"),
    MISSING_IDEMPOTENCY_KEY("missing_idempotency_key"),
    STORE_NOT_FOUND("store_not_found"),
    STORE_SCOPE_MISMATCH("store_scope_mismatch"),
    STORE_ACCESS_DENIED("store_access_denied"),
    RESERVATION_NOT_FOUND("reservation_not_found"),
    RESERVATION_CANNOT_NO_SHOW_DRAFT("reservation_cannot_no_show_draft"),
    RESERVATION_CANNOT_NO_SHOW_SEATED("reservation_cannot_no_show_seated"),
    RESERVATION_CANNOT_NO_SHOW_CANCELLED("reservation_cannot_no_show_cancelled"),
    RESERVATION_CANNOT_NO_SHOW_COMPLETED("reservation_cannot_no_show_completed"),
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

    ReservationNoShowError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ReservationNoShowError fromCode(String code) {
        return Arrays.stream(values())
            .filter(error -> error.code.equals(code))
            .findFirst()
            .orElse(INVALID_COMMAND);
    }
}
