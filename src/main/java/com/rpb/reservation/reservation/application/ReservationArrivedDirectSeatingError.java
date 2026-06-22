package com.rpb.reservation.reservation.application;

import java.util.Arrays;

public enum ReservationArrivedDirectSeatingError {
    INVALID_COMMAND("invalid_command"),
    MISSING_IDEMPOTENCY_KEY("missing_idempotency_key"),
    RESOURCE_SELECTION_CONFLICT("resource_selection_conflict"),
    RESOURCE_SELECTION_REQUIRED("resource_selection_required"),
    STORE_NOT_FOUND("store_not_found"),
    STORE_SCOPE_MISMATCH("store_scope_mismatch"),
    STORE_ACCESS_DENIED("store_access_denied"),
    RESERVATION_NOT_FOUND("reservation_not_found"),
    RESERVATION_STATUS_NOT_ARRIVED("reservation_status_not_arrived"),
    RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING("reservation_seated_without_active_seating"),
    RESERVATION_CANNOT_SEAT_CANCELLED("reservation_cannot_seat_cancelled"),
    RESERVATION_CANNOT_SEAT_NO_SHOW("reservation_cannot_seat_no_show"),
    RESERVATION_CANNOT_SEAT_COMPLETED("reservation_cannot_seat_completed"),
    TABLE_NOT_FOUND("table_not_found"),
    TABLE_NOT_AVAILABLE("table_not_available"),
    TABLE_CAPACITY_INSUFFICIENT("table_capacity_insufficient"),
    TABLE_LOCK_CONFLICT("table_lock_conflict"),
    TABLE_GROUP_NOT_FOUND("table_group_not_found"),
    TABLE_GROUP_INVALID("table_group_invalid"),
    TABLE_GROUP_MEMBER_UNAVAILABLE("table_group_member_unavailable"),
    TABLE_GROUP_CAPACITY_INSUFFICIENT("table_group_capacity_insufficient"),
    INVALID_SEATING_SOURCE("invalid_seating_source"),
    INVALID_SEATING_RESOURCE("invalid_seating_resource"),
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

    ReservationArrivedDirectSeatingError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ReservationArrivedDirectSeatingError fromCode(String code) {
        if ("table_resource_unavailable".equals(code)) {
            return TABLE_NOT_AVAILABLE;
        }
        if ("invalid_table_group".equals(code)) {
            return TABLE_GROUP_INVALID;
        }
        if ("party_size_outside_capacity".equals(code)) {
            return TABLE_CAPACITY_INSUFFICIENT;
        }
        return Arrays.stream(values())
            .filter(error -> error.code.equals(code))
            .findFirst()
            .orElse(INVALID_COMMAND);
    }
}
