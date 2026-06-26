package com.rpb.reservation.platformbilling.application;

public record PlatformProductLineCreateCommand(
    String appKey,
    String displayName,
    String status,
    String defaultEntryRoute,
    String description,
    Integer sortOrder
) {
}
