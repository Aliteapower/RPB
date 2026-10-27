package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.customer.application.CustomerManagementListResult;
import java.util.List;

public record TenantAdminCustomerListResponse(
    boolean success,
    List<TenantAdminCustomerItemResponse> customers,
    TenantAdminPageResponse page
) {
    public static TenantAdminCustomerListResponse from(CustomerManagementListResult result, java.util.Map<java.util.UUID, com.rpb.reservation.customerauth.application.CustomerActivationState> states) {
        return new TenantAdminCustomerListResponse(
            true,
            result.customers().stream().map(c -> TenantAdminCustomerItemResponse.from(c,states.getOrDefault(c.id(),com.rpb.reservation.customerauth.application.CustomerActivationState.unknown).name())).toList(),
            new TenantAdminPageResponse(result.limit(), result.offset(), result.total())
        );
    }
}
