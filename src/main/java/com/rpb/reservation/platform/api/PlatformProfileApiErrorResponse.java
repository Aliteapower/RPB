package com.rpb.reservation.platform.api;

import java.util.Map;

public record PlatformProfileApiErrorResponse(boolean success, ErrorBody error) {
    public static PlatformProfileApiErrorResponse of(PlatformProfileApiErrorCode code) {
        return new PlatformProfileApiErrorResponse(
            false,
            new ErrorBody(code.name(), code.messageKey(), Map.of())
        );
    }

    public record ErrorBody(String code, String messageKey, Map<String, Object> details) {
    }
}
