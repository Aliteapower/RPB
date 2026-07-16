package com.rpb.reservation.tenantadmin.api;

import java.util.List;
import java.util.UUID;

public record TenantAdminStaffMutationRequest(
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
