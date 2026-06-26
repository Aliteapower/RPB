package com.rpb.reservation.tenantadmin.application;

public record TenantAdminTableMutationCommand(
    String areaName,
    String tableCode,
    Integer capacity,
    Boolean enabled,
    Integer areaSortOrder,
    Integer tableSortOrder
) {
}
