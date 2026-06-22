package com.rpb.reservation.turnover.value;

import java.util.Objects;
import java.util.UUID;

/**
 * Turnover identity value.
 */
public record TurnoverId(UUID value) {

    public TurnoverId {
        Objects.requireNonNull(value, "turnover_id_required");
    }
}
