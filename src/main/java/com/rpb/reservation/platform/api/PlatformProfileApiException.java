package com.rpb.reservation.platform.api;

public class PlatformProfileApiException extends RuntimeException {
    private final PlatformProfileApiErrorCode code;

    public PlatformProfileApiException(PlatformProfileApiErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public PlatformProfileApiErrorCode code() {
        return code;
    }
}
