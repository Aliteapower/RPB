package com.rpb.reservation.queuedisplay.application;

import java.util.List;
import java.util.UUID;

public record PlatformCallScreenSeedSet(
    UUID id,
    String seedKey,
    String displayName,
    String adType,
    String status,
    List<PlatformCallScreenSeedSlide> slides,
    int version
) {
    public PlatformCallScreenSeedSet {
        slides = slides == null ? List.of() : List.copyOf(slides);
    }
}
