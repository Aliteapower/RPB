package com.rpb.reservation.tenantadmin.api;

public record TenantAdminCustomerArchiveResponse(
    boolean success
) {
    public static TenantAdminCustomerArchiveResponse ok() {
        return new TenantAdminCustomerArchiveResponse(true);
    }
}
