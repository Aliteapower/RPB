package com.rpb.reservation.platform.api;

import com.rpb.reservation.platform.application.PlatformProfile;
import com.rpb.reservation.platform.application.PlatformSocialLink;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PlatformProfileResponse(
    boolean success,
    ProfileItemResponse profile
) {
    public static PlatformProfileResponse from(PlatformProfile profile) {
        return new PlatformProfileResponse(true, ProfileItemResponse.from(profile));
    }

    public record ProfileItemResponse(
        String platformName,
        String uen,
        String address,
        String phone,
        String email,
        String website,
        String logoMediaUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        int version,
        List<SocialLinkItemResponse> socialLinks
    ) {
        private static ProfileItemResponse from(PlatformProfile profile) {
            return new ProfileItemResponse(
                profile.platformName(),
                profile.uen(),
                profile.address(),
                profile.phone(),
                profile.email(),
                profile.website(),
                platformMediaUrl(profile.logoMediaAssetId()),
                profile.createdAt(),
                profile.updatedAt(),
                profile.version(),
                profile.socialLinks().stream().map(SocialLinkItemResponse::from).toList()
            );
        }
    }

    public record SocialLinkItemResponse(
        UUID id,
        String displayName,
        String url,
        String logoMediaUrl,
        int sortOrder,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        int version
    ) {
        private static SocialLinkItemResponse from(PlatformSocialLink socialLink) {
            return new SocialLinkItemResponse(
                socialLink.id(),
                socialLink.displayName(),
                socialLink.url(),
                platformMediaUrl(socialLink.logoMediaAssetId()),
                socialLink.sortOrder(),
                socialLink.status(),
                socialLink.createdAt(),
                socialLink.updatedAt(),
                socialLink.version()
            );
        }
    }

    private static String platformMediaUrl(UUID assetId) {
        return assetId == null ? null : CallScreenMediaService.platformMediaUrl(assetId);
    }
}
