package com.rpb.reservation.platform.application;

import java.util.List;

public record PlatformTenantListResult(
    List<PlatformTenant> tenants,
    PlatformTenantPage page
) {
}
