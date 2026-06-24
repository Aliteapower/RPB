package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationArrivedDirectSeatingError;
import com.rpb.reservation.reservation.application.ReservationArrivedDirectSeatingResult;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ReservationArrivedDirectSeatingApiErrorMapper {

    public ResponseEntity<ReservationApiErrorResponse> toResponse(ReservationArrivedDirectSeatingResult result) {
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

    private ReservationApiErrorCode toApiErrorCode(ReservationArrivedDirectSeatingError error) {
        return switch (error) {
            case INVALID_COMMAND -> ReservationApiErrorCode.INVALID_COMMAND;
            case MISSING_IDEMPOTENCY_KEY -> ReservationApiErrorCode.MISSING_IDEMPOTENCY_KEY;
            case RESOURCE_SELECTION_CONFLICT -> ReservationApiErrorCode.RESOURCE_SELECTION_CONFLICT;
            case RESOURCE_SELECTION_REQUIRED -> ReservationApiErrorCode.RESOURCE_SELECTION_REQUIRED;
            case STORE_NOT_FOUND -> ReservationApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> ReservationApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> ReservationApiErrorCode.FORBIDDEN;
            case RESERVATION_NOT_FOUND -> ReservationApiErrorCode.RESERVATION_NOT_FOUND;
            case RESERVATION_STATUS_NOT_ARRIVED -> ReservationApiErrorCode.RESERVATION_STATUS_NOT_ARRIVED;
            case RESERVATION_NOT_TODAY -> ReservationApiErrorCode.RESERVATION_NOT_TODAY;
            case RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING -> ReservationApiErrorCode.RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING;
            case RESERVATION_CANNOT_SEAT_CANCELLED -> ReservationApiErrorCode.RESERVATION_CANNOT_SEAT_CANCELLED;
            case RESERVATION_CANNOT_SEAT_NO_SHOW -> ReservationApiErrorCode.RESERVATION_CANNOT_SEAT_NO_SHOW;
            case RESERVATION_CANNOT_SEAT_COMPLETED -> ReservationApiErrorCode.RESERVATION_CANNOT_SEAT_COMPLETED;
            case TABLE_NOT_FOUND -> ReservationApiErrorCode.TABLE_NOT_FOUND;
            case TABLE_NOT_AVAILABLE -> ReservationApiErrorCode.TABLE_NOT_AVAILABLE;
            case TABLE_CAPACITY_INSUFFICIENT -> ReservationApiErrorCode.TABLE_CAPACITY_INSUFFICIENT;
            case TABLE_LOCK_CONFLICT -> ReservationApiErrorCode.TABLE_LOCK_CONFLICT;
            case TABLE_GROUP_NOT_FOUND -> ReservationApiErrorCode.TABLE_GROUP_NOT_FOUND;
            case TABLE_GROUP_INVALID -> ReservationApiErrorCode.TABLE_GROUP_INVALID;
            case TABLE_GROUP_MEMBER_UNAVAILABLE -> ReservationApiErrorCode.TABLE_GROUP_MEMBER_UNAVAILABLE;
            case TABLE_GROUP_CAPACITY_INSUFFICIENT -> ReservationApiErrorCode.TABLE_GROUP_CAPACITY_INSUFFICIENT;
            case INVALID_SEATING_SOURCE -> ReservationApiErrorCode.SEATING_SOURCE_INVALID;
            case INVALID_SEATING_RESOURCE -> ReservationApiErrorCode.SEATING_RESOURCE_INVALID;
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
