package com.rpb.reservation.queuedisplay.api;

import java.util.Map;

public record CallScreenAdminApiErrorResponse(boolean success, ErrorBody error) {
    public static CallScreenAdminApiErrorResponse of(CallScreenAdminApiErrorCode code) {
        return new CallScreenAdminApiErrorResponse(false, new ErrorBody(code.name(), code.messageKey(), Map.of()));
    }

    public record ErrorBody(String code, String messageKey, Map<String, Object> details) {
    }
}
