package com.rpb.reservation.platformbilling.application;

import java.time.OffsetDateTime;

public record PlatformProductLine(
    String appKey,
    String displayName,
    String status,
    String defaultEntryRoute,
    String description,
    int sortOrder,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
