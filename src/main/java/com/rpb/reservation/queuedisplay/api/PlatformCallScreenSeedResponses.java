package com.rpb.reservation.queuedisplay.api;

import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedSet;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedSlide;
import java.util.List;
import java.util.UUID;

public final class PlatformCallScreenSeedResponses {
    private PlatformCallScreenSeedResponses() {
    }

    public record TextSeedResponse(boolean success, TextSeedSetResponse seedSet) {
        public static TextSeedResponse from(PlatformCallScreenSeedSet seedSet) {
            return new TextSeedResponse(true, TextSeedSetResponse.from(seedSet));
        }
    }

    public record TextSeedSetResponse(
        UUID id,
        String seedKey,
        String displayName,
        String adType,
        String status,
        List<TextSeedSlideResponse> slides,
        int version
    ) {
        private static TextSeedSetResponse from(PlatformCallScreenSeedSet seedSet) {
            return new TextSeedSetResponse(
                seedSet.id(),
                seedSet.seedKey(),
                seedSet.displayName(),
                seedSet.adType(),
                seedSet.status(),
                seedSet.slides().stream().map(TextSeedSlideResponse::from).toList(),
                seedSet.version()
            );
        }
    }

    public record TextSeedSlideResponse(
        UUID id,
        String title,
        String subtitle,
        String tagline,
        int sortOrder,
        String status,
        int version
    ) {
        private static TextSeedSlideResponse from(PlatformCallScreenSeedSlide slide) {
            return new TextSeedSlideResponse(
                slide.id(),
                slide.title(),
                slide.subtitle(),
                slide.tagline(),
                slide.sortOrder(),
                slide.status(),
                slide.version()
            );
        }
    }

}
