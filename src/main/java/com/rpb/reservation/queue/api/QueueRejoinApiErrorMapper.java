package com.rpb.reservation.queue.api;

import com.rpb.reservation.queue.application.QueueRejoinError;
import com.rpb.reservation.queue.application.QueueRejoinResult;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class QueueRejoinApiErrorMapper {

    public ResponseEntity<QueueRejoinApiErrorResponse> toResponse(QueueRejoinResult result) {
        QueueRejoinApiErrorCode code = toApiErrorCode(result.error());
        return toResponse(code);
    }

    public ResponseEntity<QueueRejoinApiErrorResponse> toResponse(QueueRejoinApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(QueueRejoinApiErrorResponse.of(code, idempotencyFor(code)));
    }

    private ApiIdempotencyResponse idempotencyFor(QueueRejoinApiErrorCode code) {
        if (code == QueueRejoinApiErrorCode.IDEMPOTENCY_IN_PROGRESS) {
            return ApiIdempotencyResponse.started();
        }
        if (code == QueueRejoinApiErrorCode.IDEMPOTENCY_CONFLICT) {
            return ApiIdempotencyResponse.conflict();
        }
        return ApiIdempotencyResponse.failed();
    }

    private QueueRejoinApiErrorCode toApiErrorCode(QueueRejoinError error) {
        return switch (error) {
            case INVALID_COMMAND -> QueueRejoinApiErrorCode.INVALID_COMMAND;
            case MISSING_IDEMPOTENCY_KEY -> QueueRejoinApiErrorCode.MISSING_IDEMPOTENCY_KEY;
            case STORE_NOT_FOUND -> QueueRejoinApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> QueueRejoinApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> QueueRejoinApiErrorCode.FORBIDDEN;
            case QUEUE_TICKET_NOT_FOUND -> QueueRejoinApiErrorCode.QUEUE_TICKET_NOT_FOUND;
            case QUEUE_TICKET_STATUS_NOT_SKIPPED -> QueueRejoinApiErrorCode.QUEUE_TICKET_STATUS_NOT_SKIPPED;
            case QUEUE_REJOIN_EVIDENCE_INCOMPLETE -> QueueRejoinApiErrorCode.QUEUE_REJOIN_EVIDENCE_INCOMPLETE;
            case RESERVATION_NOT_FOUND -> QueueRejoinApiErrorCode.RESERVATION_NOT_FOUND;
            case RESERVATION_STATUS_NOT_ARRIVED -> QueueRejoinApiErrorCode.RESERVATION_STATUS_NOT_ARRIVED;
            case IDEMPOTENCY_CONFLICT -> QueueRejoinApiErrorCode.IDEMPOTENCY_CONFLICT;
            case IDEMPOTENCY_IN_PROGRESS -> QueueRejoinApiErrorCode.IDEMPOTENCY_IN_PROGRESS;
            case FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY -> QueueRejoinApiErrorCode.IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY;
            case ILLEGAL_STATE_TRANSITION -> QueueRejoinApiErrorCode.ILLEGAL_STATE_TRANSITION;
            case BUSINESS_EVENT_WRITE_FAILED -> QueueRejoinApiErrorCode.EVENT_WRITE_FAILED;
            case STATE_TRANSITION_WRITE_FAILED -> QueueRejoinApiErrorCode.STATE_TRANSITION_WRITE_FAILED;
            case AUDIT_WRITE_FAILED -> QueueRejoinApiErrorCode.AUDIT_WRITE_FAILED;
            case PERSISTENCE_ERROR -> QueueRejoinApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
