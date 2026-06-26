package com.rpb.reservation.queuedisplay.api;

import com.rpb.reservation.queuedisplay.application.CallScreenAdSet;
import com.rpb.reservation.queuedisplay.application.CallScreenSetting;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaAsset;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaSlide;
import com.rpb.reservation.queuedisplay.application.CallScreenTextSlide;
import java.util.List;
import java.util.UUID;

public final class CallScreenAdminResponses {
    private CallScreenAdminResponses() {
    }

    public record SettingsResponse(boolean success, SettingResponse settings) {
        public static SettingsResponse from(CallScreenSetting setting) {
            return new SettingsResponse(true, SettingResponse.from(setting));
        }
    }

    public record AdSetResponse(boolean success, AdSetItemResponse adSet) {
        public static AdSetResponse from(CallScreenAdSet adSet) {
            return new AdSetResponse(true, AdSetItemResponse.from(adSet));
        }
    }

    public record AdSetListResponse(boolean success, List<AdSetItemResponse> adSets) {
        public static AdSetListResponse from(List<CallScreenAdSet> adSets) {
            return new AdSetListResponse(true, adSets.stream().map(AdSetItemResponse::from).toList());
        }
    }

    public record MediaAssetResponse(boolean success, MediaAssetItemResponse media) {
        public static MediaAssetResponse from(CallScreenMediaAsset media) {
            return new MediaAssetResponse(true, MediaAssetItemResponse.from(media));
        }
    }

    public record SettingResponse(
        UUID activeAdSetId,
        String adMode,
        String status,
        int slideDurationSeconds,
        int statePollSeconds,
        boolean showWaitingPreview,
        int version
    ) {
        private static SettingResponse from(CallScreenSetting setting) {
            return new SettingResponse(
                setting.activeAdSetId(),
                setting.adMode(),
                setting.status(),
                setting.slideDurationSeconds(),
                setting.statePollSeconds(),
                setting.showWaitingPreview(),
                setting.version()
            );
        }
    }

    public record AdSetItemResponse(
        UUID id,
        String name,
        String adType,
        String status,
        List<TextSlideResponse> slides,
        List<MediaSlideResponse> mediaSlides,
        int version
    ) {
        private static AdSetItemResponse from(CallScreenAdSet adSet) {
            return new AdSetItemResponse(
                adSet.id(),
                adSet.name(),
                adSet.adType(),
                adSet.status(),
                adSet.slides().stream().map(TextSlideResponse::from).toList(),
                adSet.mediaSlides().stream().map(MediaSlideResponse::from).toList(),
                adSet.version()
            );
        }
    }

    public record TextSlideResponse(
        UUID id,
        String title,
        String subtitle,
        String tagline,
        int sortOrder,
        String status,
        int version
    ) {
        private static TextSlideResponse from(CallScreenTextSlide slide) {
            return new TextSlideResponse(
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

    public record MediaSlideResponse(
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
        private static MediaSlideResponse from(CallScreenMediaSlide slide) {
            return new MediaSlideResponse(
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
