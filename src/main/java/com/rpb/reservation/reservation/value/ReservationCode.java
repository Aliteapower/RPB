package com.rpb.reservation.reservation.value;

/**
 * Store-scoped human-facing reservation code.
 */
public record ReservationCode(String value) {

    public ReservationCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reservation_code_required");
        }
    }
}
