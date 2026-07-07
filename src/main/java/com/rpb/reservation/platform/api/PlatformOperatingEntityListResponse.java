package com.rpb.reservation.platform.api;

import com.rpb.reservation.platform.application.PlatformOperatingEntity;
import java.util.List;

public record PlatformOperatingEntityListResponse(
    boolean success,
    List<PlatformOperatingEntityItemResponse> operatingEntities
) {
    public static PlatformOperatingEntityListResponse from(List<PlatformOperatingEntity> operatingEntities) {
        return new PlatformOperatingEntityListResponse(
            true,
            operatingEntities.stream().map(PlatformOperatingEntityItemResponse::from).toList()
        );
    }
}
