package com.rpb.reservation.platform.api;

import org.springframework.http.HttpStatus;

public enum PlatformTenantApiErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "platform.tenants.unauthenticated"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "platform.tenants.forbidden"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "platform.tenants.request_invalid"),
    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "platform.tenants.not_found"),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "platform.tenants.media_not_found"),
    TENANT_CODE_CONFLICT(HttpStatus.CONFLICT, "platform.tenants.tenant_code_conflict"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "platform.tenants.persistence_error"),
    AUDIT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "platform.tenants.audit_write_failed");

    private final HttpStatus httpStatus;
    private final String messageKey;

    PlatformTenantApiErrorCode(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String messageKey() {
        return messageKey;
    }
}
