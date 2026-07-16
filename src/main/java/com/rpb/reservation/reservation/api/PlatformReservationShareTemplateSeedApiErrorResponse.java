package com.rpb.reservation.reservation.api;

import java.util.Map;

public record PlatformReservationShareTemplateSeedApiErrorResponse(boolean success, ErrorBody error) {
    public static PlatformReservationShareTemplateSeedApiErrorResponse of(
        PlatformReservationShareTemplateSeedApiErrorCode code
    ) {
        return new PlatformReservationShareTemplateSeedApiErrorResponse(
            false,
            new ErrorBody(code.name(), code.messageKey(), Map.of())
        );
    }

    public record ErrorBody(String code, String messageKey, Map<String, Object> details) {
    }
}
