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
    String accountType,
    boolean self,
    boolean editable,
    boolean statusEditable,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
