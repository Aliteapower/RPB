package com.rpb.reservation.queue.api;

import com.rpb.reservation.queue.application.SeatingFromCalledQueueError;
import com.rpb.reservation.queue.application.SeatingFromCalledQueueResult;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class SeatingFromCalledQueueApiErrorMapper {

    public ResponseEntity<SeatingFromCalledQueueApiErrorResponse> toResponse(SeatingFromCalledQueueResult result) {
        SeatingFromCalledQueueApiErrorCode code = toApiErrorCode(result.error());
        return toResponse(code);
    }

    public ResponseEntity<SeatingFromCalledQueueApiErrorResponse> toResponse(SeatingFromCalledQueueApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(SeatingFromCalledQueueApiErrorResponse.of(code, idempotencyFor(code)));
    }

    private ApiIdempotencyResponse idempotencyFor(SeatingFromCalledQueueApiErrorCode code) {
        if (code == SeatingFromCalledQueueApiErrorCode.IDEMPOTENCY_IN_PROGRESS) {
            return ApiIdempotencyResponse.started();
        }
        if (code == SeatingFromCalledQueueApiErrorCode.IDEMPOTENCY_CONFLICT) {
            return ApiIdempotencyResponse.conflict();
        }
        return ApiIdempotencyResponse.failed();
    }

    private SeatingFromCalledQueueApiErrorCode toApiErrorCode(SeatingFromCalledQueueError error) {
        return switch (error) {
            case INVALID_COMMAND -> SeatingFromCalledQueueApiErrorCode.INVALID_COMMAND;
            case MISSING_IDEMPOTENCY_KEY -> SeatingFromCalledQueueApiErrorCode.MISSING_IDEMPOTENCY_KEY;
            case RESOURCE_SELECTION_CONFLICT -> SeatingFromCalledQueueApiErrorCode.RESOURCE_SELECTION_CONFLICT;
            case RESOURCE_SELECTION_REQUIRED -> SeatingFromCalledQueueApiErrorCode.RESOURCE_SELECTION_REQUIRED;
            case STORE_NOT_FOUND -> SeatingFromCalledQueueApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> SeatingFromCalledQueueApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> SeatingFromCalledQueueApiErrorCode.FORBIDDEN;
            case QUEUE_TICKET_NOT_FOUND -> SeatingFromCalledQueueApiErrorCode.QUEUE_TICKET_NOT_FOUND;
            case QUEUE_TICKET_STATUS_NOT_CALLED -> SeatingFromCalledQueueApiErrorCode.QUEUE_TICKET_STATUS_NOT_CALLED;
            case QUEUE_TICKET_SOURCE_NOT_RESERVATION -> SeatingFromCalledQueueApiErrorCode.QUEUE_TICKET_SOURCE_NOT_RESERVATION;
            case QUEUE_TICKET_CALL_EVIDENCE_INCOMPLETE -> SeatingFromCalledQueueApiErrorCode.QUEUE_TICKET_CALL_EVIDENCE_INCOMPLETE;
            case QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_SEATING -> SeatingFromCalledQueueApiErrorCode.QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_SEATING;
            case QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_RESOURCE -> SeatingFromCalledQueueApiErrorCode.QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_RESOURCE;
            case QUEUE_TICKET_SEATED_WITH_RESERVATION_NOT_SEATED -> SeatingFromCalledQueueApiErrorCode.QUEUE_TICKET_SEATED_WITH_RESERVATION_NOT_SEATED;
            case QUEUE_TICKET_CANNOT_SEAT_CANCELLED -> SeatingFromCalledQueueApiErrorCode.QUEUE_TICKET_CANNOT_SEAT_CANCELLED;
            case QUEUE_TICKET_CANNOT_SEAT_EXPIRED -> SeatingFromCalledQueueApiErrorCode.QUEUE_TICKET_CANNOT_SEAT_EXPIRED;
            case RESERVATION_NOT_FOUND -> SeatingFromCalledQueueApiErrorCode.RESERVATION_NOT_FOUND;
            case RESERVATION_STATUS_NOT_ARRIVED -> SeatingFromCalledQueueApiErrorCode.RESERVATION_STATUS_NOT_ARRIVED;
            case RESERVATION_SEATED_WITHOUT_QUEUE_TICKET_SEATED -> SeatingFromCalledQueueApiErrorCode.RESERVATION_SEATED_WITHOUT_QUEUE_TICKET_SEATED;
            case TABLE_NOT_FOUND -> SeatingFromCalledQueueApiErrorCode.TABLE_NOT_FOUND;
            case TABLE_NOT_AVAILABLE -> SeatingFromCalledQueueApiErrorCode.TABLE_NOT_AVAILABLE;
            case TABLE_CAPACITY_INSUFFICIENT -> SeatingFromCalledQueueApiErrorCode.TABLE_CAPACITY_INSUFFICIENT;
            case TABLE_LOCK_CONFLICT -> SeatingFromCalledQueueApiErrorCode.TABLE_LOCK_CONFLICT;
            case TABLE_GROUP_NOT_FOUND -> SeatingFromCalledQueueApiErrorCode.TABLE_GROUP_NOT_FOUND;
            case TABLE_GROUP_INVALID -> SeatingFromCalledQueueApiErrorCode.TABLE_GROUP_INVALID;
            case TABLE_GROUP_MEMBER_UNAVAILABLE -> SeatingFromCalledQueueApiErrorCode.TABLE_GROUP_MEMBER_UNAVAILABLE;
            case TABLE_GROUP_CAPACITY_INSUFFICIENT -> SeatingFromCalledQueueApiErrorCode.TABLE_GROUP_CAPACITY_INSUFFICIENT;
            case INVALID_SEATING_SOURCE -> SeatingFromCalledQueueApiErrorCode.SEATING_SOURCE_INVALID;
            case INVALID_SEATING_RESOURCE -> SeatingFromCalledQueueApiErrorCode.SEATING_RESOURCE_INVALID;
            case IDEMPOTENCY_CONFLICT -> SeatingFromCalledQueueApiErrorCode.IDEMPOTENCY_CONFLICT;
            case IDEMPOTENCY_IN_PROGRESS -> SeatingFromCalledQueueApiErrorCode.IDEMPOTENCY_IN_PROGRESS;
            case FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY -> SeatingFromCalledQueueApiErrorCode.IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY;
            case ILLEGAL_STATE_TRANSITION -> SeatingFromCalledQueueApiErrorCode.ILLEGAL_STATE_TRANSITION;
            case BUSINESS_EVENT_WRITE_FAILED -> SeatingFromCalledQueueApiErrorCode.EVENT_WRITE_FAILED;
            case STATE_TRANSITION_WRITE_FAILED -> SeatingFromCalledQueueApiErrorCode.STATE_TRANSITION_WRITE_FAILED;
            case AUDIT_WRITE_FAILED -> SeatingFromCalledQueueApiErrorCode.AUDIT_WRITE_FAILED;
            case PERSISTENCE_ERROR -> SeatingFromCalledQueueApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
