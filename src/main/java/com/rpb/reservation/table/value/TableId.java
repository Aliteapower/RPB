package com.rpb.reservation.table.value;

import java.util.Objects;
import java.util.UUID;

/**
 * DiningTable identity value.
 */
public record TableId(UUID value) {

    public TableId {
        Objects.requireNonNull(value, "table_id_required");
    }
}
