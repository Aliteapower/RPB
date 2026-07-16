package com.rpb.reservation.table.api;

import org.springframework.http.HttpStatus;

public enum TableSwitchApiErrorCode {
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "table_switch.store_not_found"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "table_switch.store_scope_mismatch"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "table_switch.forbidden"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "table_switch.missing_idempotency_key"),
    TABLE_SWITCH_TARGET_INVALID(HttpStatus.BAD_REQUEST, "table_switch.target_invalid"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "table_switch.idempotency_conflict"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "table_switch.idempotency_in_progress"),
    IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY(HttpStatus.CONFLICT, "table_switch.idempotency_failed_requires_new_key"),
    SEATING_NOT_FOUND(HttpStatus.NOT_FOUND, "table_switch.seating_not_found"),
    SEATING_NOT_OCCUPIED(HttpStatus.CONFLICT, "table_switch.seating_not_occupied"),
    ACTIVE_SEATING_RESOURCE_NOT_FOUND(HttpStatus.CONFLICT, "table_switch.active_seating_resource_not_found"),
    TABLE_SWITCH_TARGET_SAME_AS_CURRENT(HttpStatus.CONFLICT, "table_switch.target_same_as_current"),
    TABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "table_switch.table_not_found"),
    TABLE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "table_switch.table_group_not_found"),
    TABLE_NOT_AVAILABLE(HttpStatus.CONFLICT, "table_switch.table_not_available"),
    TABLE_GROUP_INVALID(HttpStatus.CONFLICT, "table_switch.table_group_invalid"),
    TABLE_CAPACITY_INSUFFICIENT(HttpStatus.CONFLICT, "table_switch.table_capacity_insufficient"),
    TABLE_GROUP_CAPACITY_INSUFFICIENT(HttpStatus.CONFLICT, "table_switch.table_group_capacity_insufficient"),
    TABLE_LOCK_CONFLICT(HttpStatus.CONFLICT, "table_switch.table_lock_conflict"),
    TABLE_RESOURCE_UNAVAILABLE(HttpStatus.CONFLICT, "table_switch.table_resource_unavailable"),
    CLEANING_ALREADY_ACTIVE(HttpStatus.CONFLICT, "table_switch.cleaning_already_active"),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT, "table_switch.illegal_state_transition"),
    AUDIT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "table_switch.audit_write_failed"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "table_switch.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    TableSwitchApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
