package com.rpb.reservation.queuedisplay.application;

import java.util.UUID;

public record PlatformCallScreenMediaSeedSlideCommand(
    UUID id,
    UUID mediaAssetId,
    String mediaKind,
    String title,
    String altText,
    Integer sortOrder,
    String status,
    Integer version
) {
}
