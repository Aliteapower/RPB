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

    public record MediaSeedRequest(
        String displayName,
        String status,
        List<MediaSeedSlideRequest> mediaSlides,
        Integer version
    ) {
    }

    public record MediaSeedSlideRequest(
        UUID id,
        UUID mediaAssetId,
        String mediaKind,
        String title,
        String altText,
        Integer sortOrder,
        String status,
        Integer version
    ) {
    }
}
