package com.rpb.reservation.queuedisplay.application;

import java.util.List;
import java.util.UUID;

public record PlatformCallScreenMediaSeedSet(
    UUID id,
    String seedKey,
    String displayName,
    String adType,
    String status,
    List<PlatformCallScreenMediaSeedSlide> mediaSlides,
    int version
) {
    public PlatformCallScreenMediaSeedSet {
        mediaSlides = mediaSlides == null ? List.of() : List.copyOf(mediaSlides);
    }
}
