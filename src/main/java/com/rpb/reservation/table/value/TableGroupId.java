package com.rpb.reservation.table.value;

import java.util.Objects;
import java.util.UUID;

/**
 * TableGroup identity value.
 */
public record TableGroupId(UUID value) {

    public TableGroupId {
        Objects.requireNonNull(value, "table_group_id_required");
    }
}
