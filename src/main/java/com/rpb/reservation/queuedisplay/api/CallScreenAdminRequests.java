package com.rpb.reservation.queuedisplay.api;

import java.util.List;
import java.util.UUID;

public final class CallScreenAdminRequests {
    private CallScreenAdminRequests() {
    }

    public record SettingsRequest(
        UUID activeAdSetId,
        String adMode,
        String status,
        Integer slideDurationSeconds,
        Integer statePollSeconds,
        Boolean showWaitingPreview,
        Integer version
    ) {
    }

    public record AdSetRequest(
        String name,
        String adType,
        String status,
        List<TextSlideRequest> slides,
        List<MediaSlideRequest> mediaSlides,
        Integer version
    ) {
        public AdSetRequest(
            String name,
            String adType,
            String status,
            List<TextSlideRequest> slides,
            Integer version
        ) {
            this(name, adType, status, slides, List.of(), version);
        }
    }

    public record TextSlideRequest(
        UUID id,
        String title,
        String subtitle,
        String tagline,
        Integer sortOrder,
        String status,
        Integer version
    ) {
    }

    public record MediaSlideRequest(
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
