package com.rpb.reservation.queuedisplay.api;

import java.util.List;
import java.util.UUID;

public final class PlatformCallScreenSeedRequests {
    private PlatformCallScreenSeedRequests() {
    }

    public record TextSeedRequest(
        String displayName,
        String status,
        List<TextSeedSlideRequest> slides,
        Integer version
    ) {
    }

    public record TextSeedSlideRequest(
        UUID id,
        String title,
        String subtitle,
        String tagline,
        Integer sortOrder,
        String status,
        Integer version
    ) {
    }

}
