package com.rpb.reservation.queuedisplay.application;

import java.util.List;

public record QueueDisplayAds(String mode, int slideDurationSeconds, int statePollSeconds, List<QueueDisplayAdSlide> slides) {
    public QueueDisplayAds {
        mode = mode == null || mode.isBlank() ? "text" : mode;
        slideDurationSeconds = slideDurationSeconds <= 0 ? 5 : slideDurationSeconds;
        statePollSeconds = statePollSeconds <= 0 ? 3 : statePollSeconds;
        slides = slides == null ? List.of() : List.copyOf(slides);
    }
}
