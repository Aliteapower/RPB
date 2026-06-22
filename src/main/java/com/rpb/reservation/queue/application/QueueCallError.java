package com.rpb.reservation.queue.application;

import java.util.Arrays;
import java.util.Map;

public enum QueueCallError {
    INVALID_COMMAND("invalid_command"),
    MISSING_IDEMPOTENCY_KEY("missing_idempotency_key"),
    STORE_NOT_FOUND("store_not_found"),
    STORE_SCOPE_MISMATCH("store_scope_mismatch"),
    STORE_ACCESS_DENIED("store_access_denied"),
    QUEUE_TICKET_NOT_FOUND("queue_ticket_not_found"),
    QUEUE_TICKET_STATUS_NOT_WAITING("queue_ticket_status_not_waiting"),
    QUEUE_CALL_EVIDENCE_INCOMPLETE("queue_call_evidence_incomplete"),
    QUEUE_TICKET_CANNOT_CALL_SEATED("queue_ticket_cannot_call_seated"),
    QUEUE_TICKET_CANNOT_CALL_CANCELLED("queue_ticket_cannot_call_cancelled"),
    QUEUE_TICKET_CANNOT_CALL_EXPIRED("queue_ticket_cannot_call_expired"),
    RESERVATION_NOT_FOUND("reservation_not_found"),
    RESERVATION_STATUS_NOT_ARRIVED("reservation_status_not_arrived"),
    QUEUE_CALL_HOLD_POLICY_INVALID("queue_call_hold_policy_invalid"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    IDEMPOTENCY_IN_PROGRESS("idempotency_in_progress"),
    FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY("failed_idempotency_requires_new_key"),
    ILLEGAL_STATE_TRANSITION("illegal_state_transition"),
    BUSINESS_EVENT_WRITE_FAILED("business_event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED("state_transition_write_failed"),
    AUDIT_WRITE_FAILED("audit_write_failed"),
    PERSISTENCE_ERROR("persistence_error");

    private static final Map<String, QueueCallError> ALIASES = Map.of(
        "command_in_progress", IDEMPOTENCY_IN_PROGRESS
    );

    private final String code;

    QueueCallError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static QueueCallError fromCode(String code) {
        if (ALIASES.containsKey(code)) {
            return ALIASES.get(code);
        }
        return Arrays.stream(values())
            .filter(error -> error.code.equals(code))
            .findFirst()
            .orElse(INVALID_COMMAND);
    }
}
