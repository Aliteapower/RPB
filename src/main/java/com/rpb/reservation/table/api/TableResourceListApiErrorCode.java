package com.rpb.reservation.table.api;

import org.springframework.http.HttpStatus;

public enum TableResourceListApiErrorCode {
    FORBIDDEN(HttpStatus.FORBIDDEN, "table.resources.forbidden"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "table.resources.store_scope_mismatch"),
    INVALID_STATUS(HttpStatus.BAD_REQUEST, "table.resources.invalid_status"),
    INVALID_PARTY_SIZE(HttpStatus.BAD_REQUEST, "table.resources.invalid_party_size"),
    INVALID_BUSINESS_DATE(HttpStatus.BAD_REQUEST, "table.resources.invalid_business_date"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "table.resources.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    TableResourceListApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
