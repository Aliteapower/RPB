package com.rpb.reservation.queuedisplay.application;

import java.util.UUID;

public record PlatformCallScreenMediaSeedSlide(
    UUID id,
    UUID mediaAssetId,
    String mediaKind,
    String mediaUrl,
    String title,
    String altText,
    int sortOrder,
    String status,
    int version
) {
}
