package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.TenantAdminTableListResult;
import java.util.List;

public record TenantAdminTableListResponse(
    boolean success,
    List<TenantAdminTableItemResponse> tables,
    TenantAdminPageResponse page
) {
    public static TenantAdminTableListResponse from(TenantAdminTableListResult result) {
        return new TenantAdminTableListResponse(
            true,
            result.tables().stream().map(TenantAdminTableItemResponse::from).toList(),
            TenantAdminPageResponse.from(result.page())
        );
    }
}
