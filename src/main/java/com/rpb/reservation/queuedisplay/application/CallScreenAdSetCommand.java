package com.rpb.reservation.queuedisplay.application;

import java.util.List;

public record CallScreenAdSetCommand(
    String name,
    String adType,
    String status,
    List<CallScreenTextSlideCommand> slides,
    Integer version
) {
    public CallScreenAdSetCommand {
        slides = slides == null ? List.of() : List.copyOf(slides);
    }
}
