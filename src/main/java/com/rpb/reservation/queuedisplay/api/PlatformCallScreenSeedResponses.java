package com.rpb.reservation.queuedisplay.api;

import com.rpb.reservation.queuedisplay.application.CallScreenMediaAsset;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenMediaSeedSet;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenMediaSeedSlide;
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

    public record MediaSeedResponse(boolean success, MediaSeedSetResponse seedSet) {
        public static MediaSeedResponse from(PlatformCallScreenMediaSeedSet seedSet) {
            return new MediaSeedResponse(true, MediaSeedSetResponse.from(seedSet));
        }
    }

    public record MediaAssetResponse(boolean success, MediaAssetItemResponse media) {
        public static MediaAssetResponse from(CallScreenMediaAsset media) {
            return new MediaAssetResponse(true, MediaAssetItemResponse.from(media));
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

    public record MediaSeedSetResponse(
        UUID id,
        String seedKey,
        String displayName,
        String adType,
        String status,
        List<MediaSeedSlideResponse> mediaSlides,
        int version
    ) {
        private static MediaSeedSetResponse from(PlatformCallScreenMediaSeedSet seedSet) {
            return new MediaSeedSetResponse(
                seedSet.id(),
                seedSet.seedKey(),
                seedSet.displayName(),
                "media",
                seedSet.status(),
                seedSet.mediaSlides().stream().map(MediaSeedSlideResponse::from).toList(),
                seedSet.version()
            );
        }
    }

    public record MediaSeedSlideResponse(
        UUID id,
        UUID mediaAssetId,
        String mediaKind,
        String mediaUrl,
        String title,
        String altText,
        int sortOrder,
        String status,
        int version
    ) {
        private static MediaSeedSlideResponse from(PlatformCallScreenMediaSeedSlide slide) {
            return new MediaSeedSlideResponse(
                slide.id(),
                slide.mediaAssetId(),
                slide.mediaKind(),
                slide.mediaUrl(),
                slide.title(),
                slide.altText(),
                slide.sortOrder(),
                slide.status(),
                slide.version()
            );
        }
    }

    public record MediaAssetItemResponse(
        UUID id,
        String mediaKind,
        String contentType,
        long byteSize,
        String originalFilename,
        String mediaUrl,
        int version
    ) {
        private static MediaAssetItemResponse from(CallScreenMediaAsset media) {
            return new MediaAssetItemResponse(
                media.id(),
                media.mediaKind(),
                media.contentType(),
                media.byteSize(),
                media.originalFilename(),
                media.mediaUrl(),
                media.version()
            );
        }
    }
}
