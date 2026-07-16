package com.rpb.reservation.queuedisplay.application;

import java.util.UUID;

public record PlatformCallScreenSeedSlide(
    UUID id,
    String title,
    String subtitle,
    String tagline,
    int sortOrder,
    String status,
    int version
) {
}
