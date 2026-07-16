package com.rpb.reservation.tenantadmin.application;

public class TenantAdminServiceException extends RuntimeException {
    private final TenantAdminServiceErrorCode code;

    public TenantAdminServiceException(TenantAdminServiceErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public TenantAdminServiceErrorCode code() {
        return code;
    }
}
