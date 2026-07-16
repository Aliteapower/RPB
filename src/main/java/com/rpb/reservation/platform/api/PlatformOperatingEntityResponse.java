package com.rpb.reservation.platform.api;

import com.rpb.reservation.platform.application.PlatformOperatingEntity;

public record PlatformOperatingEntityResponse(
    boolean success,
    PlatformOperatingEntityItemResponse operatingEntity
) {
    public static PlatformOperatingEntityResponse from(PlatformOperatingEntity operatingEntity) {
        return new PlatformOperatingEntityResponse(true, PlatformOperatingEntityItemResponse.from(operatingEntity));
    }
}
