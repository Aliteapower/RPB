package com.rpb.reservation.seating.value;

import java.util.Objects;
import java.util.UUID;

/**
 * Seating identity value.
 */
public record SeatingId(UUID value) {

    public SeatingId {
        Objects.requireNonNull(value, "seating_id_required");
    }
}
