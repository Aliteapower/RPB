package com.rpb.reservation.platformbilling.application;

import java.util.UUID;

public record PlatformBillingOperator(
    UUID userId,
    String actorType
) {
    public PlatformBillingOperator {
        if (actorType == null || actorType.isBlank()) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        actorType = actorType.trim();
    }
}
