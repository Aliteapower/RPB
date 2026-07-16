package com.rpb.reservation.platformbilling.api;

public class PlatformBillingApiException extends RuntimeException {
    private final PlatformBillingApiErrorCode code;

    public PlatformBillingApiException(PlatformBillingApiErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public PlatformBillingApiErrorCode code() {
        return code;
    }
}
