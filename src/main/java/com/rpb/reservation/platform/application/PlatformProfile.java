package com.rpb.reservation.platform.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PlatformProfile(
    String platformName,
    String uen,
    String address,
    String phone,
    String email,
    String website,
    UUID logoMediaAssetId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    int version,
    List<PlatformSocialLink> socialLinks
) {
    public PlatformProfile {
        socialLinks = socialLinks == null ? List.of() : List.copyOf(socialLinks);
    }
}
