package com.rpb.reservation.platform.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlatformSocialLink(
    UUID id,
    String displayName,
    String url,
    UUID logoMediaAssetId,
    int sortOrder,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    int version
) {
}
