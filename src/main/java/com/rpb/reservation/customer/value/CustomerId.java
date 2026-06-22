package com.rpb.reservation.customer.value;

import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-scoped Customer identity value.
 */
public record CustomerId(UUID value) {

    public CustomerId {
        Objects.requireNonNull(value, "customer_id_required");
    }
}
