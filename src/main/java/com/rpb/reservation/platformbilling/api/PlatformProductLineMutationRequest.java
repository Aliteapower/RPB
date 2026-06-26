package com.rpb.reservation.platformbilling.api;

record PlatformProductLineMutationRequest(
    String displayName,
    String status,
    String description,
    Integer sortOrder
) {
}
