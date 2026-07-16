package com.rpb.reservation.queue.application;

import java.util.Arrays;
import java.util.Map;

public enum QueueCancelError {
    INVALID_COMMAND("invalid_command"),
    MISSING_IDEMPOTENCY_KEY("missing_idempotency_key"),
    STORE_NOT_FOUND("store_not_found"),
    STORE_SCOPE_MISMATCH("store_scope_mismatch"),
    STORE_ACCESS_DENIED("store_access_denied"),
    QUEUE_TICKET_NOT_FOUND("queue_ticket_not_found"),
    QUEUE_TICKET_CANNOT_CANCEL_SEATED("queue_ticket_cannot_cancel_seated"),
    QUEUE_TICKET_CANNOT_CANCEL_EXPIRED("queue_ticket_cannot_cancel_expired"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    IDEMPOTENCY_IN_PROGRESS("idempotency_in_progress"),
    FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY("failed_idempotency_requires_new_key"),
    ILLEGAL_STATE_TRANSITION("illegal_state_transition"),
    BUSINESS_EVENT_WRITE_FAILED("business_event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED("state_transition_write_failed"),
    AUDIT_WRITE_FAILED("audit_write_failed"),
    PERSISTENCE_ERROR("persistence_error");

    private static final Map<String, QueueCancelError> ALIASES = Map.of(
        "command_in_progress", IDEMPOTENCY_IN_PROGRESS
    );

    private final String code;

    QueueCancelError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static QueueCancelError fromCode(String code) {
        if (ALIASES.containsKey(code)) {
            return ALIASES.get(code);
        }
        return Arrays.stream(values())
            .filter(error -> error.code.equals(code))
            .findFirst()
            .orElse(INVALID_COMMAND);
    }
}
