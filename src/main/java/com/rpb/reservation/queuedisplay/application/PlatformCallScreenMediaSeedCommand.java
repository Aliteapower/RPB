package com.rpb.reservation.queuedisplay.application;

import java.util.List;

public record PlatformCallScreenMediaSeedCommand(
    String displayName,
    String status,
    List<PlatformCallScreenMediaSeedSlideCommand> mediaSlides,
    Integer version
) {
    public PlatformCallScreenMediaSeedCommand {
        mediaSlides = mediaSlides == null ? List.of() : List.copyOf(mediaSlides);
    }
}
