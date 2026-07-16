package com.rpb.reservation.queuedisplay.api;

public class CallScreenAdminApiException extends RuntimeException {
    private final CallScreenAdminApiErrorCode code;

    public CallScreenAdminApiException(CallScreenAdminApiErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public CallScreenAdminApiErrorCode code() {
        return code;
    }
}
