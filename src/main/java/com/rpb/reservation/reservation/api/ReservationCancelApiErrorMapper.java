package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationCancelError;
import com.rpb.reservation.reservation.application.ReservationCancelResult;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ReservationCancelApiErrorMapper {

    public ResponseEntity<ReservationApiErrorResponse> toResponse(ReservationCancelResult result) {
        ReservationApiErrorCode code = toApiErrorCode(result.error());
        return toResponse(code);
    }

    public ResponseEntity<ReservationApiErrorResponse> toResponse(ReservationApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(ReservationApiErrorResponse.of(code, idempotencyFor(code)));
    }

    private ApiIdempotencyResponse idempotencyFor(ReservationApiErrorCode code) {
        if (code == ReservationApiErrorCode.IDEMPOTENCY_IN_PROGRESS) {
            return ApiIdempotencyResponse.started();
        }
        if (code == ReservationApiErrorCode.IDEMPOTENCY_CONFLICT) {
            return ApiIdempotencyResponse.conflict();
        }
        return ApiIdempotencyResponse.failed();
    }

    private ReservationApiErrorCode toApiErrorCode(ReservationCancelError error) {
        return switch (error) {
            case INVALID_COMMAND -> ReservationApiErrorCode.INVALID_COMMAND;
            case MISSING_IDEMPOTENCY_KEY -> ReservationApiErrorCode.MISSING_IDEMPOTENCY_KEY;
            case STORE_NOT_FOUND -> ReservationApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> ReservationApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> ReservationApiErrorCode.FORBIDDEN;
            case RESERVATION_NOT_FOUND -> ReservationApiErrorCode.RESERVATION_NOT_FOUND;
            case RESERVATION_CANNOT_CANCEL_ARRIVED -> ReservationApiErrorCode.RESERVATION_CANNOT_CANCEL_ARRIVED;
            case RESERVATION_CANNOT_CANCEL_SEATED -> ReservationApiErrorCode.RESERVATION_CANNOT_CANCEL_SEATED;
            case RESERVATION_CANNOT_CANCEL_NO_SHOW -> ReservationApiErrorCode.RESERVATION_CANNOT_CANCEL_NO_SHOW;
            case RESERVATION_CANNOT_CANCEL_COMPLETED -> ReservationApiErrorCode.RESERVATION_CANNOT_CANCEL_COMPLETED;
            case IDEMPOTENCY_CONFLICT -> ReservationApiErrorCode.IDEMPOTENCY_CONFLICT;
            case COMMAND_IN_PROGRESS -> ReservationApiErrorCode.IDEMPOTENCY_IN_PROGRESS;
            case FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY -> ReservationApiErrorCode.IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY;
            case ILLEGAL_STATE_TRANSITION -> ReservationApiErrorCode.ILLEGAL_STATE_TRANSITION;
            case BUSINESS_EVENT_WRITE_FAILED -> ReservationApiErrorCode.EVENT_WRITE_FAILED;
            case STATE_TRANSITION_WRITE_FAILED -> ReservationApiErrorCode.STATE_TRANSITION_WRITE_FAILED;
            case AUDIT_WRITE_FAILED -> ReservationApiErrorCode.AUDIT_WRITE_FAILED;
            case REPOSITORY_SAVE_FAILED, PERSISTENCE_ERROR -> ReservationApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
