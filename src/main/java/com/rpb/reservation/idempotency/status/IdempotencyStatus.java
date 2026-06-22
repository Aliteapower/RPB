package com.rpb.reservation.idempotency.status;

/**
 * IdempotencyRecord statuses confirmed by schema and migration constraints.
 */
public enum IdempotencyStatus {
    STARTED("started"),
    COMPLETED("completed"),
    FAILED("failed"),
    EXPIRED("expired");

    private final String code;

    IdempotencyStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
