package com.rpb.reservation.customer.api;

import org.springframework.http.HttpStatus;

public enum CustomerPhoneLookupApiErrorCode {
    FORBIDDEN(HttpStatus.FORBIDDEN, "customer.phone_lookup.forbidden"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "customer.phone_lookup.store_scope_mismatch"),
    INVALID_PHONE_E164(HttpStatus.BAD_REQUEST, "customer.phone_lookup.invalid_phone_e164"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "customer.phone_lookup.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    CustomerPhoneLookupApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
