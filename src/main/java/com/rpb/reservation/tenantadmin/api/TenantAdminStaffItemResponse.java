package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.TenantAdminStaff;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantAdminStaffItemResponse(
    UUID id,
    String employeeNo,
    String name,
    String phone,
    String email,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static TenantAdminStaffItemResponse from(TenantAdminStaff staff) {
        return new TenantAdminStaffItemResponse(
            staff.id(),
            staff.employeeNo(),
            staff.name(),
            staff.phone(),
            staff.email(),
            staff.status(),
            staff.createdAt(),
            staff.updatedAt()
        );
    }
}
