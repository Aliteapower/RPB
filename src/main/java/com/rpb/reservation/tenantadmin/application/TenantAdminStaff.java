package com.rpb.reservation.tenantadmin.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantAdminStaff(
    UUID id,
    String employeeNo,
    String name,
    String phone,
    String email,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
