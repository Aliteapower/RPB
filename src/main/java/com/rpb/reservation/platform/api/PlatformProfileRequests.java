package com.rpb.reservation.platform.api;

public final class PlatformProfileRequests {
    private PlatformProfileRequests() {
    }

    public record ProfileRequest(
        String platformName,
        String uen,
        String address,
        String phone,
        String email,
        String website
    ) {
    }

    public record SocialLinkRequest(
        String displayName,
        String url,
        Integer sortOrder,
        String status
    ) {
    }
}
