package com.rpb.reservation.queuedisplay.application;

import java.util.List;
import java.util.UUID;

public record CallScreenAdSet(
    UUID id,
    String name,
    String adType,
    String status,
    List<CallScreenTextSlide> slides,
    int version
) {
    public CallScreenAdSet {
        slides = slides == null ? List.of() : List.copyOf(slides);
    }
}
