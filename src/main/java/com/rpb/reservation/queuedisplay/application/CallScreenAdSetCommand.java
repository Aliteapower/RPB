package com.rpb.reservation.queuedisplay.application;

import java.util.List;

public record CallScreenAdSetCommand(
    String name,
    String adType,
    String status,
    List<CallScreenTextSlideCommand> slides,
    List<CallScreenMediaSlideCommand> mediaSlides,
    Integer version
) {
    public CallScreenAdSetCommand(
        String name,
        String adType,
        String status,
        List<CallScreenTextSlideCommand> slides,
        Integer version
    ) {
        this(name, adType, status, slides, List.of(), version);
    }

    public CallScreenAdSetCommand {
        slides = slides == null ? List.of() : List.copyOf(slides);
        mediaSlides = mediaSlides == null ? List.of() : List.copyOf(mediaSlides);
    }
}
