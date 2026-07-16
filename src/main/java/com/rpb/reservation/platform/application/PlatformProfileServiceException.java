package com.rpb.reservation.platform.application;

public class PlatformProfileServiceException extends RuntimeException {
    private final PlatformProfileServiceErrorCode code;

    public PlatformProfileServiceException(PlatformProfileServiceErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public PlatformProfileServiceErrorCode code() {
        return code;
    }
}
