package com.rpb.reservation.queuedisplay.application;

import java.util.List;
import java.util.UUID;

public record CallScreenAdSet(
    UUID id,
    String name,
    String adType,
    String status,
    List<CallScreenTextSlide> slides,
    List<CallScreenMediaSlide> mediaSlides,
    int version
) {
    public CallScreenAdSet(UUID id, String name, String adType, String status, List<CallScreenTextSlide> slides, int version) {
        this(id, name, adType, status, slides, List.of(), version);
    }

    public CallScreenAdSet {
        slides = slides == null ? List.of() : List.copyOf(slides);
        mediaSlides = mediaSlides == null ? List.of() : List.copyOf(mediaSlides);
    }
}
