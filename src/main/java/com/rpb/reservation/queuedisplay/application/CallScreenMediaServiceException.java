package com.rpb.reservation.queuedisplay.application;

public class CallScreenMediaServiceException extends RuntimeException {
    private final CallScreenMediaServiceErrorCode code;

    public CallScreenMediaServiceException(CallScreenMediaServiceErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public CallScreenMediaServiceErrorCode code() {
        return code;
    }
}
