package com.rpb.reservation.walkin.application;

import java.util.Arrays;

public enum WalkInDirectSeatingError {
    INVALID_COMMAND("invalid_command"),
    STORE_NOT_FOUND("store_not_found"),
    STORE_SCOPE_MISMATCH("store_scope_mismatch"),
    STORE_ACCESS_DENIED("store_access_denied"),
    INVALID_PARTY_SIZE("invalid_party_size"),
    INVALID_CUSTOMER_IDENTITY("invalid_customer_identity"),
    INVALID_RESOURCE_SELECTION("invalid_resource_selection"),
    NO_ASSIGNABLE_TABLE("no_assignable_table"),
    TABLE_RESOURCE_UNAVAILABLE("table_resource_unavailable"),
    PARTY_SIZE_OUTSIDE_CAPACITY("party_size_outside_capacity"),
    TABLE_LOCK_CONFLICT("table_lock_conflict"),
    INVALID_TABLE_GROUP("invalid_table_group"),
    TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED("temporary_table_group_member_required"),
    TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE("temporary_table_group_member_duplicate"),
    TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE("temporary_table_group_member_unavailable"),
    TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT("temporary_table_group_capacity_insufficient"),
    TEMPORARY_TABLE_GROUP_LOCK_CONFLICT("temporary_table_group_lock_conflict"),
    TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT("temporary_table_group_preassignment_conflict"),
    MANUAL_OVERRIDE_REQUIRED("manual_override_required"),
    INVALID_SEATING_SOURCE("invalid_seating_source"),
    INVALID_SEATING_RESOURCE("invalid_seating_resource"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    COMMAND_IN_PROGRESS("command_in_progress"),
    FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY("failed_idempotency_requires_new_key"),
    ILLEGAL_STATE_TRANSITION("illegal_state_transition"),
    AUDIT_WRITE_FAILED("audit_write_failed"),
    BUSINESS_EVENT_WRITE_FAILED("business_event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED("state_transition_write_failed"),
    REPOSITORY_SAVE_FAILED("repository_save_failed");

    private final String code;

    WalkInDirectSeatingError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static WalkInDirectSeatingError fromCode(String code) {
        return Arrays.stream(values())
            .filter(error -> error.code.equals(code))
            .findFirst()
            .orElse(INVALID_COMMAND);
    }
}
