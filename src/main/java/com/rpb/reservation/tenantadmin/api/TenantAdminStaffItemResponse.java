package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.TenantAdminStaff;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TenantAdminStaffItemResponse(
    UUID id,
    String employeeNo,
    String name,
    String phone,
    String email,
    String status,
    UUID defaultStoreId,
    List<UUID> storeIds,
    String accountType,
    boolean self,
    boolean editable,
    boolean statusEditable,
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
            staff.defaultStoreId(),
            staff.storeIds(),
            staff.accountType(),
            staff.self(),
            staff.editable(),
            staff.statusEditable(),
            staff.createdAt(),
            staff.updatedAt()
        );
    }
}
