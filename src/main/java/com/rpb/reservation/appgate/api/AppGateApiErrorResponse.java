package com.rpb.reservation.appgate.api;

import java.util.Map;

public record AppGateApiErrorResponse(
    boolean success,
    ErrorBody error
) {
    public static AppGateApiErrorResponse of(String code, String messageKey, Map<String, Object> details) {
        return new AppGateApiErrorResponse(false, new ErrorBody(code, messageKey, details == null ? Map.of() : details));
    }

    public record ErrorBody(
        String code,
        String messageKey,
        Map<String, Object> details
    ) {
    }
}
