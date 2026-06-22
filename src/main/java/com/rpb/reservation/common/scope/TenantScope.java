package com.rpb.reservation.common.scope;

import com.rpb.reservation.tenant.value.TenantId;
import java.util.Objects;

/**
 * Tenant scope value boundary. It carries identity only and performs no
 * authorization, database lookup, or UI work.
 */
public record TenantScope(TenantId tenantId) {

    public TenantScope {
        Objects.requireNonNull(tenantId, "tenant_scope_missing");
    }
}
