package com.rpb.reservation.queue.api;

import com.rpb.reservation.queue.application.QueueCallError;
import com.rpb.reservation.queue.application.QueueCallResult;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class QueueCallApiErrorMapper {

    public ResponseEntity<QueueCallApiErrorResponse> toResponse(QueueCallResult result) {
        QueueCallApiErrorCode code = toApiErrorCode(result.error());
        return toResponse(code);
    }

    public ResponseEntity<QueueCallApiErrorResponse> toResponse(QueueCallApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(QueueCallApiErrorResponse.of(code, idempotencyFor(code)));
    }

    private ApiIdempotencyResponse idempotencyFor(QueueCallApiErrorCode code) {
        if (code == QueueCallApiErrorCode.IDEMPOTENCY_IN_PROGRESS) {
            return ApiIdempotencyResponse.started();
        }
        if (code == QueueCallApiErrorCode.IDEMPOTENCY_CONFLICT) {
            return ApiIdempotencyResponse.conflict();
        }
        return ApiIdempotencyResponse.failed();
    }

    private QueueCallApiErrorCode toApiErrorCode(QueueCallError error) {
        return switch (error) {
            case INVALID_COMMAND -> QueueCallApiErrorCode.INVALID_COMMAND;
            case MISSING_IDEMPOTENCY_KEY -> QueueCallApiErrorCode.MISSING_IDEMPOTENCY_KEY;
            case STORE_NOT_FOUND -> QueueCallApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> QueueCallApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> QueueCallApiErrorCode.FORBIDDEN;
            case QUEUE_TICKET_NOT_FOUND -> QueueCallApiErrorCode.QUEUE_TICKET_NOT_FOUND;
            case QUEUE_TICKET_STATUS_NOT_WAITING -> QueueCallApiErrorCode.QUEUE_TICKET_STATUS_NOT_WAITING;
            case QUEUE_CALL_EVIDENCE_INCOMPLETE -> QueueCallApiErrorCode.QUEUE_CALL_EVIDENCE_INCOMPLETE;
            case QUEUE_TICKET_CANNOT_CALL_SEATED -> QueueCallApiErrorCode.QUEUE_TICKET_CANNOT_CALL_SEATED;
            case QUEUE_TICKET_CANNOT_CALL_CANCELLED -> QueueCallApiErrorCode.QUEUE_TICKET_CANNOT_CALL_CANCELLED;
            case QUEUE_TICKET_CANNOT_CALL_EXPIRED -> QueueCallApiErrorCode.QUEUE_TICKET_CANNOT_CALL_EXPIRED;
            case RESERVATION_NOT_FOUND -> QueueCallApiErrorCode.RESERVATION_NOT_FOUND;
            case RESERVATION_STATUS_NOT_ARRIVED -> QueueCallApiErrorCode.RESERVATION_STATUS_NOT_ARRIVED;
            case QUEUE_CALL_HOLD_POLICY_INVALID -> QueueCallApiErrorCode.QUEUE_CALL_HOLD_POLICY_INVALID;
            case IDEMPOTENCY_CONFLICT -> QueueCallApiErrorCode.IDEMPOTENCY_CONFLICT;
            case IDEMPOTENCY_IN_PROGRESS -> QueueCallApiErrorCode.IDEMPOTENCY_IN_PROGRESS;
            case FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY -> QueueCallApiErrorCode.IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY;
            case ILLEGAL_STATE_TRANSITION -> QueueCallApiErrorCode.ILLEGAL_STATE_TRANSITION;
            case BUSINESS_EVENT_WRITE_FAILED -> QueueCallApiErrorCode.EVENT_WRITE_FAILED;
            case STATE_TRANSITION_WRITE_FAILED -> QueueCallApiErrorCode.STATE_TRANSITION_WRITE_FAILED;
            case AUDIT_WRITE_FAILED -> QueueCallApiErrorCode.AUDIT_WRITE_FAILED;
            case PERSISTENCE_ERROR -> QueueCallApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
