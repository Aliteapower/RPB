package com.rpb.reservation.queuedisplay.application;

import java.util.UUID;

public record PlatformCallScreenSeedSlideCommand(
    UUID id,
    String title,
    String subtitle,
    String tagline,
    Integer sortOrder,
    String status,
    Integer version
) {
}
