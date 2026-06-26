package com.rpb.reservation.queuedisplay.api;

public class PlatformCallScreenSeedApiException extends RuntimeException {
    private final PlatformCallScreenSeedApiErrorCode code;

    public PlatformCallScreenSeedApiException(PlatformCallScreenSeedApiErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public PlatformCallScreenSeedApiErrorCode code() {
        return code;
    }
}
