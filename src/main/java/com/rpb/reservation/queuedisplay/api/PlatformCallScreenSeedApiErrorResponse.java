package com.rpb.reservation.queuedisplay.api;

import java.util.Map;

public record PlatformCallScreenSeedApiErrorResponse(boolean success, ErrorBody error) {
    public static PlatformCallScreenSeedApiErrorResponse of(PlatformCallScreenSeedApiErrorCode code) {
        return new PlatformCallScreenSeedApiErrorResponse(false, new ErrorBody(code.name(), code.messageKey(), Map.of()));
    }

    public record ErrorBody(String code, String messageKey, Map<String, Object> details) {
    }
}
