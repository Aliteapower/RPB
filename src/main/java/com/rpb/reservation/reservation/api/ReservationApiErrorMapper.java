package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationCreateError;
import com.rpb.reservation.reservation.application.ReservationCreateResult;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ReservationApiErrorMapper {

    public ResponseEntity<ReservationApiErrorResponse> toResponse(ReservationCreateResult result) {
        ReservationApiErrorCode code = toApiErrorCode(result.error());
        return toResponse(code, idempotencyFor(code));
    }

    public ResponseEntity<ReservationApiErrorResponse> toResponse(ReservationApiErrorCode code) {
        return toResponse(code, idempotencyFor(code));
    }

    private ResponseEntity<ReservationApiErrorResponse> toResponse(
        ReservationApiErrorCode code,
        ApiIdempotencyResponse idempotency
    ) {
        return ResponseEntity.status(code.httpStatus()).body(ReservationApiErrorResponse.of(code, idempotency));
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

    private ReservationApiErrorCode toApiErrorCode(ReservationCreateError error) {
        return switch (error) {
            case STORE_NOT_FOUND -> ReservationApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> ReservationApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> ReservationApiErrorCode.FORBIDDEN;
            case MISSING_IDEMPOTENCY_KEY -> ReservationApiErrorCode.MISSING_IDEMPOTENCY_KEY;
            case IDEMPOTENCY_CONFLICT -> ReservationApiErrorCode.IDEMPOTENCY_CONFLICT;
            case COMMAND_IN_PROGRESS -> ReservationApiErrorCode.IDEMPOTENCY_IN_PROGRESS;
            case FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY -> ReservationApiErrorCode.IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY;
            case INVALID_PARTY_SIZE -> ReservationApiErrorCode.INVALID_PARTY_SIZE;
            case INVALID_TIME_RANGE -> ReservationApiErrorCode.INVALID_TIME_RANGE;
            case RESERVATION_START_IN_PAST -> ReservationApiErrorCode.RESERVATION_START_IN_PAST;
            case INVALID_PHONE_E164 -> ReservationApiErrorCode.INVALID_PHONE_E164;
            case RESOURCE_SELECTION_CONFLICT -> ReservationApiErrorCode.RESOURCE_SELECTION_CONFLICT;
            case TABLE_NOT_FOUND -> ReservationApiErrorCode.TABLE_NOT_FOUND;
            case TABLE_NOT_AVAILABLE -> ReservationApiErrorCode.TABLE_NOT_AVAILABLE;
            case TABLE_CAPACITY_INSUFFICIENT -> ReservationApiErrorCode.TABLE_CAPACITY_INSUFFICIENT;
            case TABLE_GROUP_NOT_FOUND -> ReservationApiErrorCode.TABLE_GROUP_NOT_FOUND;
            case TABLE_GROUP_INVALID -> ReservationApiErrorCode.TABLE_GROUP_INVALID;
            case TABLE_GROUP_CAPACITY_INSUFFICIENT -> ReservationApiErrorCode.TABLE_GROUP_CAPACITY_INSUFFICIENT;
            case CUSTOMER_NOT_FOUND -> ReservationApiErrorCode.CUSTOMER_NOT_FOUND;
            case RESERVATION_DUPLICATE_ACTIVE -> ReservationApiErrorCode.RESERVATION_DUPLICATE_ACTIVE;
            case RESERVATION_CAPACITY_INSUFFICIENT -> ReservationApiErrorCode.RESERVATION_CAPACITY_INSUFFICIENT;
            case RESERVATION_TIME_SLOT_UNAVAILABLE -> ReservationApiErrorCode.RESERVATION_TIME_SLOT_UNAVAILABLE;
            case RESERVATION_CODE_CONFLICT -> ReservationApiErrorCode.RESERVATION_CODE_CONFLICT;
            case ILLEGAL_STATE_TRANSITION -> ReservationApiErrorCode.ILLEGAL_STATE_TRANSITION;
            case AUDIT_WRITE_FAILED -> ReservationApiErrorCode.AUDIT_WRITE_FAILED;
            case BUSINESS_EVENT_WRITE_FAILED -> ReservationApiErrorCode.EVENT_WRITE_FAILED;
            case STATE_TRANSITION_WRITE_FAILED -> ReservationApiErrorCode.STATE_TRANSITION_WRITE_FAILED;
            case REPOSITORY_SAVE_FAILED, PERSISTENCE_ERROR -> ReservationApiErrorCode.PERSISTENCE_ERROR;
            case INVALID_COMMAND -> ReservationApiErrorCode.INVALID_CUSTOMER_IDENTITY;
        };
    }
}
