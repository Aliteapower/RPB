package com.rpb.reservation.platform.application;

public class PlatformTenantServiceException extends RuntimeException {
    private final PlatformTenantServiceErrorCode code;

    public PlatformTenantServiceException(PlatformTenantServiceErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public PlatformTenantServiceErrorCode code() {
        return code;
    }
}
