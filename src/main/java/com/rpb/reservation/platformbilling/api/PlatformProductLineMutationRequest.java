package com.rpb.reservation.platformbilling.api;

record PlatformProductLineMutationRequest(
    String displayName,
    String status,
    String defaultEntryRoute,
    String description,
    Integer sortOrder
) {
}

record PlatformProductLineCreateRequest(
    String appKey,
    String displayName,
    String status,
    String defaultEntryRoute,
    String description,
    Integer sortOrder
) {
}
