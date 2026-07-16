package com.rpb.reservation.auth.api;

import java.util.Map;

public record AuthApiErrorResponse(
    boolean success,
    ErrorBody error
) {
    public static AuthApiErrorResponse of(AuthApiErrorCode code, Map<String, Object> details) {
        return new AuthApiErrorResponse(
            false,
            new ErrorBody(code.name(), code.messageKey(), details == null ? Map.of() : Map.copyOf(details))
        );
    }

    public record ErrorBody(
        String code,
        String messageKey,
        Map<String, Object> details
    ) {
    }
}
