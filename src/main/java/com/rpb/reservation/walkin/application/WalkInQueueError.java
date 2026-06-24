package com.rpb.reservation.walkin.application;

import java.util.Arrays;
import java.util.Map;

public enum WalkInQueueError {
    INVALID_COMMAND("invalid_command"),
    MISSING_IDEMPOTENCY_KEY("missing_idempotency_key"),
    INVALID_PARTY_SIZE("invalid_party_size"),
    STORE_NOT_FOUND("store_not_found"),
    STORE_SCOPE_MISMATCH("store_scope_mismatch"),
    STORE_ACCESS_DENIED("store_access_denied"),
    INVALID_CUSTOMER_IDENTITY("invalid_customer_identity"),
    QUEUE_GROUP_NOT_FOUND("queue_group_not_found"),
    QUEUE_GROUP_PARTY_SIZE_MISMATCH("queue_group_party_size_mismatch"),
    QUEUE_TICKET_NUMBER_CONFLICT("queue_ticket_number_conflict"),
    IDEMPOTENCY_IN_PROGRESS("idempotency_in_progress"),
    FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY("failed_idempotency_requires_new_key"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    BUSINESS_EVENT_WRITE_FAILED("business_event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED("state_transition_write_failed"),
    AUDIT_WRITE_FAILED("audit_write_failed"),
    PERSISTENCE_ERROR("persistence_error");

    private static final Map<String, WalkInQueueError> ALIASES = Map.of(
        "command_in_progress", IDEMPOTENCY_IN_PROGRESS,
        "queue_group_not_found", QUEUE_GROUP_NOT_FOUND,
        "queue_group_party_size_mismatch", QUEUE_GROUP_PARTY_SIZE_MISMATCH
    );

    private final String code;

    WalkInQueueError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static WalkInQueueError fromCode(String code) {
        if (ALIASES.containsKey(code)) {
            return ALIASES.get(code);
        }
        return Arrays.stream(values())
            .filter(error -> error.code.equals(code))
            .findFirst()
            .orElse(INVALID_COMMAND);
    }
}
