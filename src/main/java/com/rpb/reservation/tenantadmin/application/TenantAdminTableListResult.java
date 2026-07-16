package com.rpb.reservation.tenantadmin.application;

import java.util.List;

public record TenantAdminTableListResult(
    List<TenantAdminTable> tables,
    TenantAdminPage page
) {
}
