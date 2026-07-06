package com.rpb.reservation.tenantadmin.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TenantAdminStaff(
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
    public TenantAdminStaff {
        storeIds = storeIds == null ? List.of() : List.copyOf(storeIds);
    }
}
