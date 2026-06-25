package com.rpb.reservation.platform.api;

import com.rpb.reservation.platform.application.PlatformTenant;

public record PlatformTenantResponse(
    boolean success,
    PlatformTenantItemResponse tenant
) {
    public static PlatformTenantResponse from(PlatformTenant tenant) {
        return new PlatformTenantResponse(true, PlatformTenantItemResponse.from(tenant));
    }
}
