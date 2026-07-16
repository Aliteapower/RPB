package com.rpb.reservation.walkin.api;

import com.rpb.reservation.walkin.application.WalkInQueueError;
import com.rpb.reservation.walkin.application.WalkInQueueResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class WalkInQueueApiErrorMapper {

    public ResponseEntity<WalkInQueueApiErrorResponse> toResponse(WalkInQueueResult result) {
        return toResponse(toApiErrorCode(result.error()));
    }

    public ResponseEntity<WalkInQueueApiErrorResponse> toResponse(WalkInQueueApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(WalkInQueueApiErrorResponse.of(code, idempotencyFor(code)));
    }

    private ApiIdempotencyResponse idempotencyFor(WalkInQueueApiErrorCode code) {
        if (code == WalkInQueueApiErrorCode.IDEMPOTENCY_IN_PROGRESS) {
            return ApiIdempotencyResponse.started();
        }
        if (code == WalkInQueueApiErrorCode.IDEMPOTENCY_CONFLICT) {
            return ApiIdempotencyResponse.conflict();
        }
        return ApiIdempotencyResponse.failed();
    }

    private WalkInQueueApiErrorCode toApiErrorCode(WalkInQueueError error) {
        return switch (error) {
            case INVALID_COMMAND -> WalkInQueueApiErrorCode.INVALID_COMMAND;
            case MISSING_IDEMPOTENCY_KEY -> WalkInQueueApiErrorCode.MISSING_IDEMPOTENCY_KEY;
            case INVALID_PARTY_SIZE -> WalkInQueueApiErrorCode.INVALID_PARTY_SIZE;
            case STORE_NOT_FOUND -> WalkInQueueApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> WalkInQueueApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> WalkInQueueApiErrorCode.FORBIDDEN;
            case INVALID_CUSTOMER_IDENTITY -> WalkInQueueApiErrorCode.INVALID_CUSTOMER_IDENTITY;
            case QUEUE_GROUP_NOT_FOUND -> WalkInQueueApiErrorCode.QUEUE_GROUP_NOT_FOUND;
            case QUEUE_GROUP_PARTY_SIZE_MISMATCH -> WalkInQueueApiErrorCode.QUEUE_GROUP_PARTY_SIZE_MISMATCH;
            case QUEUE_TICKET_NUMBER_CONFLICT -> WalkInQueueApiErrorCode.QUEUE_TICKET_NUMBER_CONFLICT;
            case IDEMPOTENCY_CONFLICT -> WalkInQueueApiErrorCode.IDEMPOTENCY_CONFLICT;
            case IDEMPOTENCY_IN_PROGRESS -> WalkInQueueApiErrorCode.IDEMPOTENCY_IN_PROGRESS;
            case FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY -> WalkInQueueApiErrorCode.IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY;
            case BUSINESS_EVENT_WRITE_FAILED -> WalkInQueueApiErrorCode.EVENT_WRITE_FAILED;
            case STATE_TRANSITION_WRITE_FAILED -> WalkInQueueApiErrorCode.STATE_TRANSITION_WRITE_FAILED;
            case AUDIT_WRITE_FAILED -> WalkInQueueApiErrorCode.AUDIT_WRITE_FAILED;
            case PERSISTENCE_ERROR -> WalkInQueueApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
