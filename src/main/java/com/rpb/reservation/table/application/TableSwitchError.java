package com.rpb.reservation.table.application;

import java.util.Arrays;

public enum TableSwitchError {
    INVALID_COMMAND("invalid_command"),
    STORE_NOT_FOUND("store_not_found"),
    STORE_SCOPE_MISMATCH("store_scope_mismatch"),
    STORE_ACCESS_DENIED("store_access_denied"),
    SEATING_NOT_FOUND("seating_not_found"),
    SEATING_NOT_OCCUPIED("seating_not_occupied"),
    ACTIVE_SEATING_RESOURCE_NOT_FOUND("active_seating_resource_not_found"),
    TARGET_REQUIRED("target_required"),
    TARGET_AMBIGUOUS("target_ambiguous"),
    TARGET_SAME_AS_CURRENT("target_same_as_current"),
    TABLE_NOT_FOUND("table_not_found"),
    TABLE_GROUP_NOT_FOUND("table_group_not_found"),
    TABLE_NOT_AVAILABLE("table_not_available"),
    TABLE_GROUP_INVALID("table_group_invalid"),
    TABLE_CAPACITY_INSUFFICIENT("table_capacity_insufficient"),
    TABLE_GROUP_CAPACITY_INSUFFICIENT("table_group_capacity_insufficient"),
    TABLE_LOCK_CONFLICT("table_lock_conflict"),
    TABLE_RESOURCE_UNAVAILABLE("table_resource_unavailable"),
    CLEANING_ALREADY_ACTIVE("cleaning_already_active"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    COMMAND_IN_PROGRESS("command_in_progress"),
    FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY("failed_idempotency_requires_new_key"),
    ILLEGAL_STATE_TRANSITION("illegal_state_transition"),
    AUDIT_WRITE_FAILED("audit_write_failed"),
    BUSINESS_EVENT_WRITE_FAILED("business_event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED("state_transition_write_failed"),
    REPOSITORY_SAVE_FAILED("repository_save_failed");

    private final String code;

    TableSwitchError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static TableSwitchError fromCode(String code) {
        return Arrays.stream(values())
            .filter(error -> error.code.equals(code))
            .findFirst()
            .orElse(INVALID_COMMAND);
    }
}
