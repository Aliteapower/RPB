package com.rpb.reservation.tenantadmin.application;

record TenantAdminTableExcelRow(
    int areaSortOrder,
    int tableSortOrder,
    String areaName,
    String tableCode,
    int capacity,
    boolean enabled
) {
}
