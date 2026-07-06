package com.rpb.reservation.tenantadmin.application;

import java.util.List;
import java.util.UUID;

public record TenantAdminStaffMutationCommand(
    String employeeNo,
    String name,
    String phone,
    String email,
    String status,
    String password,
    List<UUID> storeIds,
    UUID defaultStoreId
) {
}
