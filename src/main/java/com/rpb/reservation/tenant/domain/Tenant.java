package com.rpb.reservation.tenant.domain;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.Objects;

/**
 * Tenant domain skeleton. Tenant is the isolation root and is not Store.
 */
public record Tenant(TenantId id, String tenantCode, String displayName, String status) {

    public Tenant {
        Objects.requireNonNull(id, "tenant_id_required");
        requireText(tenantCode, "tenant_code_required");
        requireText(displayName, "tenant_display_name_required");
        requireText(status, "tenant_status_required");
    }

    public static Tenant skeleton(TenantId id, String tenantCode, String displayName, String status) {
        return new Tenant(id, tenantCode, displayName, status);
    }

    public TenantScope scope() {
        return new TenantScope(id);
    }

    public String activateIntent() {
        return "tenant.activate.intent";
    }

    public String domainBoundary() {
        return "Tenant is not Store and does not own store operations directly.";
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
