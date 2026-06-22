package com.rpb.reservation.reservation.value;

import java.util.Objects;
import java.util.UUID;

/**
 * Reservation identity value.
 */
public record ReservationId(UUID value) {

    public ReservationId {
        Objects.requireNonNull(value, "reservation_id_required");
    }
}
