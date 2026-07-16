package com.rpb.reservation.tenantadmin.api;

public class TenantAdminApiException extends RuntimeException {
    private final TenantAdminApiErrorCode code;

    public TenantAdminApiException(TenantAdminApiErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public TenantAdminApiErrorCode code() {
        return code;
    }
}
