package com.rpb.reservation.cleaning.value;

import java.util.Objects;
import java.util.UUID;

/**
 * Cleaning identity value.
 */
public record CleaningId(UUID value) {

    public CleaningId {
        Objects.requireNonNull(value, "cleaning_id_required");
    }
}
