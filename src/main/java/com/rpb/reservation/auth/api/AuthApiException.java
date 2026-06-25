package com.rpb.reservation.auth.api;

import java.util.Map;

public class AuthApiException extends RuntimeException {
    private final AuthApiErrorCode code;
    private final Map<String, Object> details;

    public AuthApiException(AuthApiErrorCode code) {
        this(code, Map.of());
    }

    public AuthApiException(AuthApiErrorCode code, Map<String, Object> details) {
        super(code.messageKey());
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public AuthApiErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
