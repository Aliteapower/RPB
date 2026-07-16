package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationCompleteError;
import com.rpb.reservation.reservation.application.ReservationCompleteResult;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ReservationCompleteApiErrorMapper {

    public ResponseEntity<ReservationApiErrorResponse> toResponse(ReservationCompleteResult result) {
        return toResponse(toApiErrorCode(result.error()));
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

    private ReservationApiErrorCode toApiErrorCode(ReservationCompleteError error) {
        return switch (error) {
            case INVALID_COMMAND -> ReservationApiErrorCode.INVALID_COMMAND;
            case MISSING_IDEMPOTENCY_KEY -> ReservationApiErrorCode.MISSING_IDEMPOTENCY_KEY;
            case STORE_NOT_FOUND -> ReservationApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> ReservationApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> ReservationApiErrorCode.FORBIDDEN;
            case RESERVATION_NOT_FOUND -> ReservationApiErrorCode.RESERVATION_NOT_FOUND;
            case RESERVATION_CANNOT_COMPLETE_DRAFT -> ReservationApiErrorCode.RESERVATION_CANNOT_COMPLETE_DRAFT;
            case RESERVATION_CANNOT_COMPLETE_CONFIRMED -> ReservationApiErrorCode.RESERVATION_CANNOT_COMPLETE_CONFIRMED;
            case RESERVATION_CANNOT_COMPLETE_ARRIVED -> ReservationApiErrorCode.RESERVATION_CANNOT_COMPLETE_ARRIVED;
            case RESERVATION_CANNOT_COMPLETE_CANCELLED -> ReservationApiErrorCode.RESERVATION_CANNOT_COMPLETE_CANCELLED;
            case RESERVATION_CANNOT_COMPLETE_NO_SHOW -> ReservationApiErrorCode.RESERVATION_CANNOT_COMPLETE_NO_SHOW;
            case RESERVATION_COMPLETED_WITHOUT_ACTIVE_SEATING -> ReservationApiErrorCode.RESERVATION_COMPLETED_WITHOUT_ACTIVE_SEATING;
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
