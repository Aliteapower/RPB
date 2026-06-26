package com.rpb.reservation.queuedisplay.application;

public class PlatformCallScreenSeedServiceException extends RuntimeException {
    private final PlatformCallScreenSeedServiceErrorCode code;

    public PlatformCallScreenSeedServiceException(PlatformCallScreenSeedServiceErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public PlatformCallScreenSeedServiceErrorCode code() {
        return code;
    }
}
