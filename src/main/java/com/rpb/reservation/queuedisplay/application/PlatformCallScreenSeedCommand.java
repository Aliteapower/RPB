package com.rpb.reservation.queuedisplay.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record PlatformCallScreenSeedCommand(
    String displayName,
    String status,
    List<PlatformCallScreenSeedSlideCommand> slides,
    Integer version
) {
    public PlatformCallScreenSeedCommand {
        slides = slides == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(slides));
    }
}
