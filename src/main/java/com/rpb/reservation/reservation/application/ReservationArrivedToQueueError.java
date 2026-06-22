package com.rpb.reservation.reservation.application;

import java.util.Arrays;

public enum ReservationArrivedToQueueError {
    INVALID_COMMAND("invalid_command"),
    MISSING_IDEMPOTENCY_KEY("missing_idempotency_key"),
    STORE_NOT_FOUND("store_not_found"),
    STORE_SCOPE_MISMATCH("store_scope_mismatch"),
    STORE_ACCESS_DENIED("store_access_denied"),
    RESERVATION_NOT_FOUND("reservation_not_found"),
    RESERVATION_STATUS_NOT_ARRIVED("reservation_status_not_arrived"),
    RESERVATION_CANNOT_QUEUE_SEATED("reservation_cannot_queue_seated"),
    RESERVATION_CANNOT_QUEUE_CANCELLED("reservation_cannot_queue_cancelled"),
    RESERVATION_CANNOT_QUEUE_NO_SHOW("reservation_cannot_queue_no_show"),
    RESERVATION_CANNOT_QUEUE_COMPLETED("reservation_cannot_queue_completed"),
    QUEUE_GROUP_NOT_FOUND("queue_group_not_found"),
    QUEUE_GROUP_CANNOT_BE_DERIVED("queue_group_cannot_be_derived"),
    QUEUE_GROUP_PARTY_SIZE_MISMATCH("queue_group_party_size_mismatch"),
    QUEUE_TICKET_NUMBER_CONFLICT("queue_ticket_number_conflict"),
    ACTIVE_QUEUE_TICKET_CONFLICT("active_queue_ticket_conflict"),
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

    ReservationArrivedToQueueError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ReservationArrivedToQueueError fromCode(String code) {
        return Arrays.stream(values())
            .filter(error -> error.code.equals(code))
            .findFirst()
            .orElse(INVALID_COMMAND);
    }
}
