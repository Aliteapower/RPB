package com.rpb.reservation.tenantadmin.api;

public record TenantAdminCustomerMutationRequest(
    String displayName,
    String nickname,
    String phoneE164,
    String email
) {
}
