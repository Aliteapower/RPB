package com.rpb.reservation.staffhome.api;

import org.springframework.http.HttpStatus;

public enum StaffHomeOverviewApiErrorCode {
    INVALID_QUERY(HttpStatus.BAD_REQUEST, "staff_home.overview.invalid_query"),
    INVALID_BUSINESS_DATE(HttpStatus.BAD_REQUEST, "staff_home.overview.invalid_business_date"),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "staff_home.overview.store_not_found"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "staff_home.overview.store_scope_mismatch"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "staff_home.overview.forbidden"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "staff_home.overview.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    StaffHomeOverviewApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
