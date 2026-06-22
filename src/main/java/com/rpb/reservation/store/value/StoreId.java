package com.rpb.reservation.store.value;

import java.util.Objects;
import java.util.UUID;

/**
 * Store identity value. Store owns operational scope.
 */
public record StoreId(UUID value) {

    public StoreId {
        Objects.requireNonNull(value, "store_id_required");
    }
}
