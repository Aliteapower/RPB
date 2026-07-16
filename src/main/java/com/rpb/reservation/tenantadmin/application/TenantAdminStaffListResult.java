package com.rpb.reservation.tenantadmin.application;

import java.util.List;

public record TenantAdminStaffListResult(
    List<TenantAdminStaff> staff,
    TenantAdminPage page
) {
}
