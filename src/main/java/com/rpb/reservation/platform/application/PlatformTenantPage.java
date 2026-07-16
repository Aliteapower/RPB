package com.rpb.reservation.platform.application;

public record PlatformTenantPage(
    int limit,
    int offset,
    int total
) {
}
