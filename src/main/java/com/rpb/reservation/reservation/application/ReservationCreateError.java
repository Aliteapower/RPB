package com.rpb.reservation.reservation.application;

import java.util.Arrays;

public enum ReservationCreateError {
    INVALID_COMMAND("invalid_command"),
    MISSING_IDEMPOTENCY_KEY("missing_idempotency_key"),
    STORE_NOT_FOUND("store_not_found"),
    STORE_SCOPE_MISMATCH("store_scope_mismatch"),
    STORE_ACCESS_DENIED("store_access_denied"),
    INVALID_PARTY_SIZE("invalid_party_size"),
    INVALID_TIME_RANGE("invalid_time_range"),
    RESERVATION_START_IN_PAST("reservation_start_in_past"),
    CUSTOMER_NOT_FOUND("customer_not_found"),
    INVALID_PHONE_E164("invalid_phone_e164"),
    RESERVATION_DUPLICATE_ACTIVE("reservation_duplicate_active"),
    RESERVATION_CAPACITY_INSUFFICIENT("reservation_capacity_insufficient"),
    RESERVATION_CODE_CONFLICT("reservation_code_conflict"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    COMMAND_IN_PROGRESS("command_in_progress"),
    FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY("failed_idempotency_requires_new_key"),
    ILLEGAL_STATE_TRANSITION("illegal_state_transition"),
    AUDIT_WRITE_FAILED("audit_write_failed"),
    BUSINESS_EVENT_WRITE_FAILED("business_event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED("state_transition_write_failed"),
    REPOSITORY_SAVE_FAILED("repository_save_failed"),
    PERSISTENCE_ERROR("persistence_error");

    private final String code;

    ReservationCreateError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ReservationCreateError fromCode(String code) {
        return Arrays.stream(values())
            .filter(error -> error.code.equals(code))
            .findFirst()
            .orElse(INVALID_COMMAND);
    }
}
