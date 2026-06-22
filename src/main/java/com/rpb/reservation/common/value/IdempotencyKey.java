package com.rpb.reservation.common.value;

/**
 * Caller or system supplied idempotency key for repeatable critical actions.
 */
public record IdempotencyKey(String value) {

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("idempotency_key_required");
        }
    }
}
