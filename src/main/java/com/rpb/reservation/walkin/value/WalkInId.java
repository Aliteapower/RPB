package com.rpb.reservation.walkin.value;

import java.util.Objects;
import java.util.UUID;

/**
 * WalkIn identity value.
 */
public record WalkInId(UUID value) {

    public WalkInId {
        Objects.requireNonNull(value, "walk_in_id_required");
    }
}
