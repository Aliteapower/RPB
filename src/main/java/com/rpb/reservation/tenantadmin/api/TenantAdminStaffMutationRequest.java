package com.rpb.reservation.tenantadmin.api;

public record TenantAdminStaffMutationRequest(
    String employeeNo,
    String name,
    String phone,
    String email,
    String status,
    String password
) {
}
