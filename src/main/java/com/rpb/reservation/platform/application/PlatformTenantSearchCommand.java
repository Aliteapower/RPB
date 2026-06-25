package com.rpb.reservation.platform.application;

public record PlatformTenantSearchCommand(
    String keyword,
    String status,
    boolean includeDeleted,
    String limit,
    String offset
) {
}
