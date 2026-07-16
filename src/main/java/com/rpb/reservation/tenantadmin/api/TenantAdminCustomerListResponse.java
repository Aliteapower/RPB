package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.customer.application.CustomerManagementListResult;
import java.util.List;

public record TenantAdminCustomerListResponse(
    boolean success,
    List<TenantAdminCustomerItemResponse> customers,
    TenantAdminPageResponse page
) {
    public static TenantAdminCustomerListResponse from(CustomerManagementListResult result) {
        return new TenantAdminCustomerListResponse(
            true,
            result.customers().stream().map(TenantAdminCustomerItemResponse::from).toList(),
            new TenantAdminPageResponse(result.limit(), result.offset(), result.total())
        );
    }
}
