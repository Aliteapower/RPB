package com.rpb.reservation.platform.application;

public record PlatformTenantSearchCriteria(
    String keyword,
    String status,
    boolean includeDeleted,
    int limit,
    int offset
) {
}
