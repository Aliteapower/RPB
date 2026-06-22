package com.rpb.reservation.common.value;

/**
 * Stable reason code value for cancellation, no-show, skip, override, cleaning,
 * and table release boundaries.
 */
public record ReasonCodeValue(String value) {

    public ReasonCodeValue {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reason_code_required");
        }
    }
}
