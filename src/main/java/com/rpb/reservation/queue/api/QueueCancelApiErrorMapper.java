package com.rpb.reservation.queue.api;

import com.rpb.reservation.queue.application.QueueCancelError;
import com.rpb.reservation.queue.application.QueueCancelResult;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class QueueCancelApiErrorMapper {

    public ResponseEntity<QueueCancelApiErrorResponse> toResponse(QueueCancelResult result) {
        return toResponse(toApiErrorCode(result.error()));
    }

    public ResponseEntity<QueueCancelApiErrorResponse> toResponse(QueueCancelApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(QueueCancelApiErrorResponse.of(code, idempotencyFor(code)));
    }

    private ApiIdempotencyResponse idempotencyFor(QueueCancelApiErrorCode code) {
        if (code == QueueCancelApiErrorCode.IDEMPOTENCY_IN_PROGRESS) {
            return ApiIdempotencyResponse.started();
        }
        if (code == QueueCancelApiErrorCode.IDEMPOTENCY_CONFLICT) {
            return ApiIdempotencyResponse.conflict();
        }
        return ApiIdempotencyResponse.failed();
    }

    private QueueCancelApiErrorCode toApiErrorCode(QueueCancelError error) {
        return switch (error) {
            case INVALID_COMMAND -> QueueCancelApiErrorCode.INVALID_COMMAND;
            case MISSING_IDEMPOTENCY_KEY -> QueueCancelApiErrorCode.MISSING_IDEMPOTENCY_KEY;
            case STORE_NOT_FOUND -> QueueCancelApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> QueueCancelApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> QueueCancelApiErrorCode.FORBIDDEN;
            case QUEUE_TICKET_NOT_FOUND -> QueueCancelApiErrorCode.QUEUE_TICKET_NOT_FOUND;
            case QUEUE_TICKET_CANNOT_CANCEL_SEATED -> QueueCancelApiErrorCode.QUEUE_TICKET_CANNOT_CANCEL_SEATED;
            case QUEUE_TICKET_CANNOT_CANCEL_EXPIRED -> QueueCancelApiErrorCode.QUEUE_TICKET_CANNOT_CANCEL_EXPIRED;
            case IDEMPOTENCY_CONFLICT -> QueueCancelApiErrorCode.IDEMPOTENCY_CONFLICT;
            case IDEMPOTENCY_IN_PROGRESS -> QueueCancelApiErrorCode.IDEMPOTENCY_IN_PROGRESS;
            case FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY -> QueueCancelApiErrorCode.IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY;
            case ILLEGAL_STATE_TRANSITION -> QueueCancelApiErrorCode.ILLEGAL_STATE_TRANSITION;
            case BUSINESS_EVENT_WRITE_FAILED -> QueueCancelApiErrorCode.EVENT_WRITE_FAILED;
            case STATE_TRANSITION_WRITE_FAILED -> QueueCancelApiErrorCode.STATE_TRANSITION_WRITE_FAILED;
            case AUDIT_WRITE_FAILED -> QueueCancelApiErrorCode.AUDIT_WRITE_FAILED;
            case PERSISTENCE_ERROR -> QueueCancelApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
