package com.rpb.reservation.platform.api;

import java.util.Map;

public record PlatformTenantApiErrorResponse(
    boolean success,
    ErrorBody error
) {
    public static PlatformTenantApiErrorResponse of(PlatformTenantApiErrorCode code) {
        return new PlatformTenantApiErrorResponse(
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
