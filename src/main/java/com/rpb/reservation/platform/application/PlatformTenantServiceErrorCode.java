package com.rpb.reservation.platform.application;

public enum PlatformTenantServiceErrorCode {
    REQUEST_INVALID,
    TENANT_NOT_FOUND,
    OPERATING_ENTITY_NOT_FOUND,
    STORE_NOT_FOUND,
    TENANT_CODE_CONFLICT,
    OPERATING_ENTITY_CODE_CONFLICT,
    STORE_CODE_CONFLICT,
    AUDIT_WRITE_FAILED
}
