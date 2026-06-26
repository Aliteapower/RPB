package com.rpb.reservation.platformbilling.application;

public class PlatformBillingServiceException extends RuntimeException {
    private final PlatformBillingServiceErrorCode code;

    public PlatformBillingServiceException(PlatformBillingServiceErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public PlatformBillingServiceErrorCode code() {
        return code;
    }
}
