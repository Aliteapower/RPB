package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.TenantAdminTableImportSummary;

public record TenantAdminTableImportResponse(
    boolean success,
    TenantAdminTableImportSummaryResponse imported
) {
    public static TenantAdminTableImportResponse from(TenantAdminTableImportSummary summary) {
        return new TenantAdminTableImportResponse(true, TenantAdminTableImportSummaryResponse.from(summary));
    }
}
