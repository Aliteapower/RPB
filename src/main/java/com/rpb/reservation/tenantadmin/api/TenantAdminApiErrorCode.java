package com.rpb.reservation.tenantadmin.api;

import org.springframework.http.HttpStatus;

public enum TenantAdminApiErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "tenant.admin.unauthenticated"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "tenant.admin.forbidden"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "tenant.admin.store_scope_mismatch"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "tenant.admin.request_invalid"),
    TENANT_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "tenant.admin.tenant_profile_not_found"),
    STAFF_NOT_FOUND(HttpStatus.NOT_FOUND, "tenant.admin.staff_not_found"),
    TABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "tenant.admin.table_not_found"),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "tenant.admin.media_not_found"),
    TEMPLATE_UNKNOWN_VARIABLE(HttpStatus.BAD_REQUEST, "tenant.admin.template_unknown_variable"),
    STAFF_CODE_CONFLICT(HttpStatus.CONFLICT, "tenant.admin.staff_code_conflict"),
    TABLE_CODE_CONFLICT(HttpStatus.CONFLICT, "tenant.admin.table_code_conflict"),
    TABLE_IN_USE(HttpStatus.CONFLICT, "tenant.admin.table_in_use"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "tenant.admin.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    TenantAdminApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
