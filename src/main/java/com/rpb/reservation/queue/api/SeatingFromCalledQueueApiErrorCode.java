package com.rpb.reservation.queue.api;

import org.springframework.http.HttpStatus;

public enum SeatingFromCalledQueueApiErrorCode {
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.seat.store_not_found"),
    STORE_SCOPE_MISMATCH(HttpStatus.FORBIDDEN, "queue.seat.store_scope_mismatch"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "queue.seat.forbidden"),
    INVALID_COMMAND(HttpStatus.BAD_REQUEST, "queue.seat.invalid_command"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "queue.seat.missing_idempotency_key"),
    RESOURCE_SELECTION_CONFLICT(HttpStatus.BAD_REQUEST, "queue.seat.resource_selection_conflict"),
    RESOURCE_SELECTION_REQUIRED(HttpStatus.BAD_REQUEST, "queue.seat.resource_selection_required"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "queue.seat.idempotency_conflict"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "queue.seat.idempotency_in_progress"),
    IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY(HttpStatus.CONFLICT, "queue.seat.idempotency_failed_requires_new_key"),
    QUEUE_TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.seat.queue_ticket_not_found"),
    QUEUE_TICKET_STATUS_NOT_CALLED(HttpStatus.CONFLICT, "queue.seat.queue_ticket_status_not_called"),
    QUEUE_TICKET_SOURCE_NOT_RESERVATION(HttpStatus.CONFLICT, "queue.seat.queue_ticket_source_not_reservation"),
    QUEUE_TICKET_CALL_EVIDENCE_INCOMPLETE(HttpStatus.CONFLICT, "queue.seat.queue_ticket_call_evidence_incomplete"),
    QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_SEATING(HttpStatus.CONFLICT, "queue.seat.queue_ticket_seated_without_active_seating"),
    QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_RESOURCE(HttpStatus.CONFLICT, "queue.seat.queue_ticket_seated_without_active_resource"),
    QUEUE_TICKET_SEATED_WITH_RESERVATION_NOT_SEATED(HttpStatus.CONFLICT, "queue.seat.queue_ticket_seated_with_reservation_not_seated"),
    QUEUE_TICKET_CANNOT_SEAT_CANCELLED(HttpStatus.CONFLICT, "queue.seat.queue_ticket_cannot_seat_cancelled"),
    QUEUE_TICKET_CANNOT_SEAT_EXPIRED(HttpStatus.CONFLICT, "queue.seat.queue_ticket_cannot_seat_expired"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.seat.reservation_not_found"),
    RESERVATION_STATUS_NOT_ARRIVED(HttpStatus.CONFLICT, "queue.seat.reservation_status_not_arrived"),
    RESERVATION_SEATED_WITHOUT_QUEUE_TICKET_SEATED(HttpStatus.CONFLICT, "queue.seat.reservation_seated_without_queue_ticket_seated"),
    TABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.seat.table_not_found"),
    TABLE_NOT_AVAILABLE(HttpStatus.CONFLICT, "queue.seat.table_not_available"),
    TABLE_CAPACITY_INSUFFICIENT(HttpStatus.CONFLICT, "queue.seat.table_capacity_insufficient"),
    TABLE_LOCK_CONFLICT(HttpStatus.CONFLICT, "queue.seat.table_lock_conflict"),
    TABLE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "queue.seat.table_group_not_found"),
    TABLE_GROUP_INVALID(HttpStatus.CONFLICT, "queue.seat.table_group_invalid"),
    TABLE_GROUP_MEMBER_UNAVAILABLE(HttpStatus.CONFLICT, "queue.seat.table_group_member_unavailable"),
    TABLE_GROUP_CAPACITY_INSUFFICIENT(HttpStatus.CONFLICT, "queue.seat.table_group_capacity_insufficient"),
    TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED(HttpStatus.BAD_REQUEST, "queue.seat.temporary_table_group_member_required"),
    TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE(HttpStatus.BAD_REQUEST, "queue.seat.temporary_table_group_member_duplicate"),
    TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE(HttpStatus.CONFLICT, "queue.seat.temporary_table_group_member_unavailable"),
    TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT(HttpStatus.CONFLICT, "queue.seat.temporary_table_group_capacity_insufficient"),
    TEMPORARY_TABLE_GROUP_LOCK_CONFLICT(HttpStatus.CONFLICT, "queue.seat.temporary_table_group_lock_conflict"),
    TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT(HttpStatus.CONFLICT, "queue.seat.temporary_table_group_preassignment_conflict"),
    SEATING_SOURCE_INVALID(HttpStatus.CONFLICT, "queue.seat.seating_source_invalid"),
    SEATING_RESOURCE_INVALID(HttpStatus.CONFLICT, "queue.seat.seating_resource_invalid"),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT, "queue.seat.illegal_state_transition"),
    AUDIT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.seat.audit_write_failed"),
    EVENT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.seat.event_write_failed"),
    STATE_TRANSITION_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "queue.seat.state_transition_write_failed"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "queue.seat.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    SeatingFromCalledQueueApiErrorCode(HttpStatus httpStatus, String messageKey) {
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
