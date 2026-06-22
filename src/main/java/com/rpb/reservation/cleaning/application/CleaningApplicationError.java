package com.rpb.reservation.cleaning.application;

import java.util.Arrays;

public enum CleaningApplicationError {
    INVALID_COMMAND("invalid_command"),
    STORE_NOT_FOUND("store_not_found"),
    STORE_SCOPE_MISMATCH("store_scope_mismatch"),
    STORE_ACCESS_DENIED("store_access_denied"),
    SEATING_NOT_FOUND("seating_not_found"),
    SEATING_RESOURCE_NOT_FOUND("seating_resource_not_found"),
    TABLE_NOT_FOUND("table_not_found"),
    INVALID_TABLE_GROUP("invalid_table_group"),
    TABLE_NOT_OCCUPIED("table_not_occupied"),
    CLEANING_ALREADY_ACTIVE("cleaning_already_active"),
    CLEANING_NOT_FOUND("cleaning_not_found"),
    CLEANING_ALREADY_COMPLETED("cleaning_already_completed"),
    TABLE_NOT_CLEANING("table_not_cleaning"),
    RESOURCE_TARGET_INVALID("resource_target_invalid"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    COMMAND_IN_PROGRESS("command_in_progress"),
    FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY("failed_idempotency_requires_new_key"),
    ILLEGAL_STATE_TRANSITION("illegal_state_transition"),
    AUDIT_WRITE_FAILED("audit_write_failed"),
    BUSINESS_EVENT_WRITE_FAILED("business_event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED("state_transition_write_failed"),
    REPOSITORY_SAVE_FAILED("repository_save_failed");

    private final String code;

    CleaningApplicationError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static CleaningApplicationError fromCode(String code) {
        return Arrays.stream(values())
            .filter(error -> error.code.equals(code))
            .findFirst()
            .orElse(INVALID_COMMAND);
    }
}
