package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.TenantAdminStaffListResult;
import java.util.List;

public record TenantAdminStaffListResponse(
    boolean success,
    List<TenantAdminStaffItemResponse> staff,
    TenantAdminPageResponse page
) {
    public static TenantAdminStaffListResponse from(TenantAdminStaffListResult result) {
        return new TenantAdminStaffListResponse(
            true,
            result.staff().stream().map(TenantAdminStaffItemResponse::from).toList(),
            TenantAdminPageResponse.from(result.page())
        );
    }
}
