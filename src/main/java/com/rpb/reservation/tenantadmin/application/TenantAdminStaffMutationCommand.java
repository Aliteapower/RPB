package com.rpb.reservation.tenantadmin.application;

public record TenantAdminStaffMutationCommand(
    String employeeNo,
    String name,
    String phone,
    String email,
    String status,
    String password
) {
}
