package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.customer.application.CustomerManagementItem;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantAdminCustomerItemResponse(
    UUID id,
    String customerCode,
    String displayName,
    String nickname,
    String phoneE164,
    String email,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static TenantAdminCustomerItemResponse from(CustomerManagementItem customer) {
        return new TenantAdminCustomerItemResponse(
            customer.id(),
            customer.customerCode(),
            customer.displayName(),
            customer.nickname(),
            customer.phoneE164(),
            customer.email(),
            customer.status(),
            customer.createdAt(),
            customer.updatedAt()
        );
    }
}
