package com.rpb.reservation.common.scope;

import java.util.Objects;

/**
 * Platform-level scope marker for data and operations without tenant or store ownership.
 */
public record PlatformScope(String platformCode) {

    public PlatformScope {
        if (platformCode == null || platformCode.isBlank()) {
            throw new IllegalArgumentException("platform_scope_missing");
        }
        platformCode = platformCode.trim();
    }

    public static PlatformScope reservationPlatform() {
        return new PlatformScope("reservation-platform");
    }

    public boolean matches(String candidatePlatformCode) {
        return Objects.equals(platformCode, candidatePlatformCode);
    }
}
