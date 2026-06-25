package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.TenantAdminStaff;

public record TenantAdminStaffResponse(
    boolean success,
    TenantAdminStaffItemResponse staff
) {
    public static TenantAdminStaffResponse from(TenantAdminStaff staff) {
        return new TenantAdminStaffResponse(true, TenantAdminStaffItemResponse.from(staff));
    }
}
