package com.rpb.reservation.queuedisplay.application;

import java.util.UUID;

public record CallScreenMediaAsset(
    UUID id,
    String ownerScope,
    UUID tenantId,
    String mediaKind,
    String contentType,
    long byteSize,
    String originalFilename,
    String storageKey,
    String mediaUrl,
    int version
) {
}
