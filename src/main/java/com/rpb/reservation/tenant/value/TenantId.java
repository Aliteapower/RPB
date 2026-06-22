package com.rpb.reservation.tenant.value;

import java.util.Objects;
import java.util.UUID;

/**
 * Tenant identity value. Tenant is the isolation boundary.
 */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "tenant_id_required");
    }
}
