package com.rpb.reservation.queue.application;

import java.util.Arrays;
import java.util.Map;

public enum SeatingFromCalledQueueError {
    INVALID_COMMAND("invalid_command"),
    MISSING_IDEMPOTENCY_KEY("missing_idempotency_key"),
    RESOURCE_SELECTION_CONFLICT("resource_selection_conflict"),
    RESOURCE_SELECTION_REQUIRED("resource_selection_required"),
    STORE_NOT_FOUND("store_not_found"),
    STORE_SCOPE_MISMATCH("store_scope_mismatch"),
    STORE_ACCESS_DENIED("store_access_denied"),
    QUEUE_TICKET_NOT_FOUND("queue_ticket_not_found"),
    QUEUE_TICKET_STATUS_NOT_CALLED("queue_ticket_status_not_called"),
    QUEUE_TICKET_SOURCE_NOT_RESERVATION("queue_ticket_source_not_reservation"),
    QUEUE_TICKET_CALL_EVIDENCE_INCOMPLETE("queue_ticket_call_evidence_incomplete"),
    QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_SEATING("queue_ticket_seated_without_active_seating"),
    QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_RESOURCE("queue_ticket_seated_without_active_resource"),
    QUEUE_TICKET_SEATED_WITH_RESERVATION_NOT_SEATED("queue_ticket_seated_with_reservation_not_seated"),
    QUEUE_TICKET_CANNOT_SEAT_CANCELLED("queue_ticket_cannot_seat_cancelled"),
    QUEUE_TICKET_CANNOT_SEAT_EXPIRED("queue_ticket_cannot_seat_expired"),
    RESERVATION_NOT_FOUND("reservation_not_found"),
    RESERVATION_STATUS_NOT_ARRIVED("reservation_status_not_arrived"),
    RESERVATION_SEATED_WITHOUT_QUEUE_TICKET_SEATED("reservation_seated_without_queue_ticket_seated"),
    TABLE_NOT_FOUND("table_not_found"),
    TABLE_NOT_AVAILABLE("table_not_available"),
    TABLE_CAPACITY_INSUFFICIENT("table_capacity_insufficient"),
    TABLE_LOCK_CONFLICT("table_lock_conflict"),
    TABLE_GROUP_NOT_FOUND("table_group_not_found"),
    TABLE_GROUP_INVALID("table_group_invalid"),
    TABLE_GROUP_MEMBER_UNAVAILABLE("table_group_member_unavailable"),
    TABLE_GROUP_CAPACITY_INSUFFICIENT("table_group_capacity_insufficient"),
    TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED("temporary_table_group_member_required"),
    TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE("temporary_table_group_member_duplicate"),
    TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE("temporary_table_group_member_unavailable"),
    TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT("temporary_table_group_capacity_insufficient"),
    TEMPORARY_TABLE_GROUP_LOCK_CONFLICT("temporary_table_group_lock_conflict"),
    TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT("temporary_table_group_preassignment_conflict"),
    INVALID_SEATING_SOURCE("invalid_seating_source"),
    INVALID_SEATING_RESOURCE("invalid_seating_resource"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    IDEMPOTENCY_IN_PROGRESS("idempotency_in_progress"),
    FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY("failed_idempotency_requires_new_key"),
    ILLEGAL_STATE_TRANSITION("illegal_state_transition"),
    BUSINESS_EVENT_WRITE_FAILED("business_event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED("state_transition_write_failed"),
    AUDIT_WRITE_FAILED("audit_write_failed"),
    PERSISTENCE_ERROR("persistence_error");

    private static final Map<String, SeatingFromCalledQueueError> ALIASES = Map.of(
        "command_in_progress", IDEMPOTENCY_IN_PROGRESS,
        "table_resource_unavailable", TABLE_NOT_AVAILABLE,
        "invalid_table_group", TABLE_GROUP_INVALID,
        "party_size_outside_capacity", TABLE_CAPACITY_INSUFFICIENT
    );

    private final String code;

    SeatingFromCalledQueueError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static SeatingFromCalledQueueError fromCode(String code) {
        if (ALIASES.containsKey(code)) {
            return ALIASES.get(code);
        }
        return Arrays.stream(values())
            .filter(error -> error.code.equals(code))
            .findFirst()
            .orElse(INVALID_COMMAND);
    }
}
