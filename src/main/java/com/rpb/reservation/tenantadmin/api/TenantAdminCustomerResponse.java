package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.customer.application.CustomerManagementItem;

public record TenantAdminCustomerResponse(
    boolean success,
    TenantAdminCustomerItemResponse customer
) {
    public static TenantAdminCustomerResponse from(CustomerManagementItem customer) {
        return new TenantAdminCustomerResponse(true, TenantAdminCustomerItemResponse.from(customer));
    }
}
