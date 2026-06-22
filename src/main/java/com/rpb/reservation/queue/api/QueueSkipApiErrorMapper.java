package com.rpb.reservation.queue.api;

import com.rpb.reservation.queue.application.QueueSkipError;
import com.rpb.reservation.queue.application.QueueSkipResult;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class QueueSkipApiErrorMapper {

    public ResponseEntity<QueueSkipApiErrorResponse> toResponse(QueueSkipResult result) {
        QueueSkipApiErrorCode code = toApiErrorCode(result.error());
        return toResponse(code);
    }

    public ResponseEntity<QueueSkipApiErrorResponse> toResponse(QueueSkipApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(QueueSkipApiErrorResponse.of(code, idempotencyFor(code)));
    }

    private ApiIdempotencyResponse idempotencyFor(QueueSkipApiErrorCode code) {
        if (code == QueueSkipApiErrorCode.IDEMPOTENCY_IN_PROGRESS) {
            return ApiIdempotencyResponse.started();
        }
        if (code == QueueSkipApiErrorCode.IDEMPOTENCY_CONFLICT) {
            return ApiIdempotencyResponse.conflict();
        }
        return ApiIdempotencyResponse.failed();
    }

    private QueueSkipApiErrorCode toApiErrorCode(QueueSkipError error) {
        return switch (error) {
            case INVALID_COMMAND -> QueueSkipApiErrorCode.INVALID_COMMAND;
            case MISSING_IDEMPOTENCY_KEY -> QueueSkipApiErrorCode.MISSING_IDEMPOTENCY_KEY;
            case STORE_NOT_FOUND -> QueueSkipApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> QueueSkipApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> QueueSkipApiErrorCode.FORBIDDEN;
            case QUEUE_TICKET_NOT_FOUND -> QueueSkipApiErrorCode.QUEUE_TICKET_NOT_FOUND;
            case QUEUE_TICKET_STATUS_NOT_CALLED -> QueueSkipApiErrorCode.QUEUE_TICKET_STATUS_NOT_CALLED;
            case QUEUE_SKIP_EVIDENCE_INCOMPLETE -> QueueSkipApiErrorCode.QUEUE_SKIP_EVIDENCE_INCOMPLETE;
            case RESERVATION_NOT_FOUND -> QueueSkipApiErrorCode.RESERVATION_NOT_FOUND;
            case RESERVATION_STATUS_NOT_ARRIVED -> QueueSkipApiErrorCode.RESERVATION_STATUS_NOT_ARRIVED;
            case IDEMPOTENCY_CONFLICT -> QueueSkipApiErrorCode.IDEMPOTENCY_CONFLICT;
            case IDEMPOTENCY_IN_PROGRESS -> QueueSkipApiErrorCode.IDEMPOTENCY_IN_PROGRESS;
            case FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY -> QueueSkipApiErrorCode.IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY;
            case ILLEGAL_STATE_TRANSITION -> QueueSkipApiErrorCode.ILLEGAL_STATE_TRANSITION;
            case BUSINESS_EVENT_WRITE_FAILED -> QueueSkipApiErrorCode.EVENT_WRITE_FAILED;
            case STATE_TRANSITION_WRITE_FAILED -> QueueSkipApiErrorCode.STATE_TRANSITION_WRITE_FAILED;
            case AUDIT_WRITE_FAILED -> QueueSkipApiErrorCode.AUDIT_WRITE_FAILED;
            case PERSISTENCE_ERROR -> QueueSkipApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
