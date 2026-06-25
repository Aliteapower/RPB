package com.rpb.reservation.tenantadmin.api;

public record TenantAdminTableMutationRequest(
    String areaName,
    String tableCode,
    Integer capacity,
    Boolean enabled
) {
}
