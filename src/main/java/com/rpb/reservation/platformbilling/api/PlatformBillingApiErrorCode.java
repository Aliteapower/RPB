package com.rpb.reservation.platformbilling.api;

import org.springframework.http.HttpStatus;

public enum PlatformBillingApiErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "platform.billing.unauthenticated"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "platform.billing.forbidden"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "platform.billing.request_invalid"),
    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "platform.billing.tenant_not_found"),
    PRODUCT_LINE_NOT_FOUND(HttpStatus.NOT_FOUND, "platform.billing.product_line_not_found"),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "platform.billing.subscription_not_found"),
    SUBSCRIPTION_CONFLICT(HttpStatus.CONFLICT, "platform.billing.subscription_conflict"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "platform.billing.version_conflict"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "platform.billing.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    PlatformBillingApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
