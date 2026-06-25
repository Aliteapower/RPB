package com.rpb.reservation.table.api;

import org.springframework.http.HttpStatus;

public enum TemporaryTableGroupApiErrorCode {
    FORBIDDEN(HttpStatus.FORBIDDEN, "table.temporary_group.forbidden"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "table.temporary_group.store_scope_mismatch"),
    INVALID_BUSINESS_DATE(HttpStatus.BAD_REQUEST, "table.temporary_group.invalid_business_date"),
    GROUP_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "table.temporary_group.group_name_required"),
    GROUP_NAME_CONFLICT(HttpStatus.CONFLICT, "table.temporary_group.group_name_conflict"),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "table.temporary_group.group_not_found"),
    GROUP_NOT_TEMPORARY(HttpStatus.CONFLICT, "table.temporary_group.group_not_temporary"),
    GROUP_NOT_DISSOLVABLE(HttpStatus.CONFLICT, "table.temporary_group.group_not_dissolvable"),
    MEMBER_REQUIRED(HttpStatus.BAD_REQUEST, "table.temporary_group.member_required"),
    MEMBER_DUPLICATE(HttpStatus.BAD_REQUEST, "table.temporary_group.member_duplicate"),
    MEMBER_UNAVAILABLE(HttpStatus.CONFLICT, "table.temporary_group.member_unavailable"),
    CAPACITY_INSUFFICIENT(HttpStatus.CONFLICT, "table.temporary_group.capacity_insufficient"),
    LOCK_CONFLICT(HttpStatus.CONFLICT, "table.temporary_group.lock_conflict"),
    PREASSIGNMENT_CONFLICT(HttpStatus.CONFLICT, "table.temporary_group.preassignment_conflict"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "table.temporary_group.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    TemporaryTableGroupApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
