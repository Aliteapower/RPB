package com.rpb.reservation.platform.api;

public class PlatformTenantApiException extends RuntimeException {
    private final PlatformTenantApiErrorCode code;

    public PlatformTenantApiException(PlatformTenantApiErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public PlatformTenantApiErrorCode code() {
        return code;
    }
}
