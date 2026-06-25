package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.TenantAdminSettings;

public record TenantAdminSettingsResponse(
    boolean success,
    TenantAdminSettings settings
) {
    public static TenantAdminSettingsResponse from(TenantAdminSettings settings) {
        return new TenantAdminSettingsResponse(true, settings);
    }
}
