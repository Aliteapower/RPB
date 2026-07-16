package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.TenantAdminTableImportSummary;

public record TenantAdminTableImportSummaryResponse(
    int totalRows,
    int created,
    int updated
) {
    static TenantAdminTableImportSummaryResponse from(TenantAdminTableImportSummary summary) {
        return new TenantAdminTableImportSummaryResponse(summary.totalRows(), summary.created(), summary.updated());
    }
}
