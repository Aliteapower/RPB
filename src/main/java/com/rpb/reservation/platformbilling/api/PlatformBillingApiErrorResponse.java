package com.rpb.reservation.platformbilling.api;

import java.util.Map;

public record PlatformBillingApiErrorResponse(
    boolean success,
    ErrorBody error
) {
    public static PlatformBillingApiErrorResponse of(PlatformBillingApiErrorCode code) {
        return new PlatformBillingApiErrorResponse(
            false,
            new ErrorBody(code.name(), code.messageKey(), Map.of())
        );
    }

    public record ErrorBody(
        String code,
        String messageKey,
        Map<String, Object> details
    ) {
    }
}
