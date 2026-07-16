package com.rpb.reservation.platform.application;

import java.util.UUID;

public record PlatformOperator(
    UUID actorId,
    String actorType
) {

    public PlatformOperator {
        if (actorType == null || actorType.isBlank()) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        actorType = actorType.trim();
    }
}
