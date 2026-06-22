package com.rpb.reservation.reservation.api;

import org.springframework.http.HttpStatus;

public enum ReservationApiErrorCode {
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "reservation.store_not_found"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "reservation.store_scope_mismatch"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "reservation.forbidden"),
    INVALID_COMMAND(HttpStatus.BAD_REQUEST, "reservation.invalid_command"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "reservation.missing_idempotency_key"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "reservation.idempotency_conflict"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "reservation.idempotency_in_progress"),
    IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY(HttpStatus.CONFLICT, "reservation.idempotency_failed_requires_new_key"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "reservation.not_found"),
    RESERVATION_STATUS_NOT_CONFIRMED(HttpStatus.CONFLICT, "reservation.status_not_confirmed"),
    RESERVATION_CANNOT_CHECK_IN_CANCELLED(HttpStatus.CONFLICT, "reservation.cannot_check_in_cancelled"),
    RESERVATION_CANNOT_CHECK_IN_NO_SHOW(HttpStatus.CONFLICT, "reservation.cannot_check_in_no_show"),
    RESERVATION_CANNOT_CHECK_IN_COMPLETED(HttpStatus.CONFLICT, "reservation.cannot_check_in_completed"),
    RESERVATION_CANNOT_CHECK_IN_SEATED(HttpStatus.CONFLICT, "reservation.cannot_check_in_seated"),
    RESOURCE_SELECTION_CONFLICT(HttpStatus.BAD_REQUEST, "reservation.resource_selection_conflict"),
    RESOURCE_SELECTION_REQUIRED(HttpStatus.BAD_REQUEST, "reservation.resource_selection_required"),
    RESERVATION_STATUS_NOT_ARRIVED(HttpStatus.CONFLICT, "reservation.status_not_arrived"),
    RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING(HttpStatus.CONFLICT, "reservation.seated_without_active_seating"),
    RESERVATION_CANNOT_SEAT_CANCELLED(HttpStatus.CONFLICT, "reservation.cannot_seat_cancelled"),
    RESERVATION_CANNOT_SEAT_NO_SHOW(HttpStatus.CONFLICT, "reservation.cannot_seat_no_show"),
    RESERVATION_CANNOT_SEAT_COMPLETED(HttpStatus.CONFLICT, "reservation.cannot_seat_completed"),
    RESERVATION_CANNOT_QUEUE_SEATED(HttpStatus.CONFLICT, "reservation.cannot_queue_seated"),
    RESERVATION_CANNOT_QUEUE_CANCELLED(HttpStatus.CONFLICT, "reservation.cannot_queue_cancelled"),
    RESERVATION_CANNOT_QUEUE_NO_SHOW(HttpStatus.CONFLICT, "reservation.cannot_queue_no_show"),
    RESERVATION_CANNOT_QUEUE_COMPLETED(HttpStatus.CONFLICT, "reservation.cannot_queue_completed"),
    TABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "reservation.table_not_found"),
    TABLE_NOT_AVAILABLE(HttpStatus.CONFLICT, "reservation.table_not_available"),
    TABLE_CAPACITY_INSUFFICIENT(HttpStatus.CONFLICT, "reservation.table_capacity_insufficient"),
    TABLE_LOCK_CONFLICT(HttpStatus.CONFLICT, "reservation.table_lock_conflict"),
    TABLE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "reservation.table_group_not_found"),
    TABLE_GROUP_INVALID(HttpStatus.CONFLICT, "reservation.table_group_invalid"),
    TABLE_GROUP_MEMBER_UNAVAILABLE(HttpStatus.CONFLICT, "reservation.table_group_member_unavailable"),
    TABLE_GROUP_CAPACITY_INSUFFICIENT(HttpStatus.CONFLICT, "reservation.table_group_capacity_insufficient"),
    SEATING_SOURCE_INVALID(HttpStatus.CONFLICT, "reservation.seating_source_invalid"),
    SEATING_RESOURCE_INVALID(HttpStatus.CONFLICT, "reservation.seating_resource_invalid"),
    QUEUE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "reservation.queue_group_not_found"),
    QUEUE_GROUP_CANNOT_BE_DERIVED(HttpStatus.CONFLICT, "reservation.queue_group_cannot_be_derived"),
    QUEUE_GROUP_PARTY_SIZE_MISMATCH(HttpStatus.CONFLICT, "reservation.queue_group_party_size_mismatch"),
    QUEUE_TICKET_NUMBER_CONFLICT(HttpStatus.CONFLICT, "reservation.queue_ticket_number_conflict"),
    ACTIVE_QUEUE_TICKET_CONFLICT(HttpStatus.CONFLICT, "reservation.active_queue_ticket_conflict"),
    INVALID_PARTY_SIZE(HttpStatus.BAD_REQUEST, "reservation.invalid_party_size"),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "reservation.invalid_time_range"),
    RESERVATION_START_IN_PAST(HttpStatus.BAD_REQUEST, "reservation.start_in_past"),
    INVALID_PHONE_E164(HttpStatus.BAD_REQUEST, "reservation.invalid_phone_e164"),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "reservation.customer_not_found"),
    INVALID_CUSTOMER_IDENTITY(HttpStatus.BAD_REQUEST, "reservation.invalid_customer_identity"),
    RESERVATION_DUPLICATE_ACTIVE(HttpStatus.CONFLICT, "reservation.duplicate_active"),
    RESERVATION_CAPACITY_INSUFFICIENT(HttpStatus.CONFLICT, "reservation.capacity_insufficient"),
    RESERVATION_CODE_CONFLICT(HttpStatus.CONFLICT, "reservation.code_conflict"),
    RESERVATION_POLICY_NOT_FOUND(HttpStatus.CONFLICT, "reservation.policy_not_found"),
    INVALID_BUSINESS_DATE(HttpStatus.BAD_REQUEST, "reservation.today_view.invalid_business_date"),
    INVALID_STATUS_FILTER(HttpStatus.BAD_REQUEST, "reservation.today_view.invalid_status_filter"),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT, "reservation.illegal_state_transition"),
    AUDIT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "reservation.audit_write_failed"),
    EVENT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "reservation.event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "reservation.state_transition_write_failed"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "reservation.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    ReservationApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
