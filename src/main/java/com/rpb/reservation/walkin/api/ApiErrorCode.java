package com.rpb.reservation.walkin.api;

import java.util.Locale;
import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    INVALID_PARTY_SIZE(HttpStatus.BAD_REQUEST),
    INVALID_PHONE_E164(HttpStatus.BAD_REQUEST),
    INVALID_CUSTOMER_IDENTITY(HttpStatus.BAD_REQUEST),
    RESOURCE_CONFLICT(HttpStatus.BAD_REQUEST),
    TABLE_NOT_AVAILABLE(HttpStatus.CONFLICT),
    TABLE_CAPACITY_INSUFFICIENT(HttpStatus.CONFLICT),
    TABLE_LOCK_CONFLICT(HttpStatus.CONFLICT),
    TABLE_INACTIVE(HttpStatus.CONFLICT),
    TABLE_GROUP_INVALID(HttpStatus.CONFLICT),
    SEATING_SOURCE_INVALID(HttpStatus.CONFLICT),
    SEATING_RESOURCE_INVALID(HttpStatus.CONFLICT),
    OVERRIDE_REASON_REQUIRED(HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT),
    IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY(HttpStatus.CONFLICT),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT),
    AUDIT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;
    private final String messageKey;

    ApiErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
        this.messageKey = "walkin.direct_seating." + name().toLowerCase(Locale.ROOT);
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String messageKey() {
        return messageKey;
    }
}
