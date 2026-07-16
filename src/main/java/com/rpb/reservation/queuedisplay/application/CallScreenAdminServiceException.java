package com.rpb.reservation.queuedisplay.application;

public class CallScreenAdminServiceException extends RuntimeException {
    private final CallScreenAdminServiceErrorCode code;

    public CallScreenAdminServiceException(CallScreenAdminServiceErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public CallScreenAdminServiceErrorCode code() {
        return code;
    }
}
