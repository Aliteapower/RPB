package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.AssignableReservationTablesResult;
import com.rpb.reservation.reservation.application.ReservationTableAssignmentError;
import com.rpb.reservation.reservation.application.ReservationTableAssignmentResult;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ReservationTableAssignmentApiErrorMapper {

    public ResponseEntity<ReservationTableAssignmentApiErrorResponse> toResponse(
        AssignableReservationTablesResult result
    ) {
        return toResponse(toApiErrorCode(result.error()), null);
    }

    public ResponseEntity<ReservationTableAssignmentApiErrorResponse> toResponse(
        ReservationTableAssignmentResult result
    ) {
        ReservationTableAssignmentApiErrorCode code = toApiErrorCode(result.error());
        return toResponse(code, idempotencyFor(code));
    }

    public ResponseEntity<ReservationTableAssignmentApiErrorResponse> toResponse(
        ReservationTableAssignmentApiErrorCode code
    ) {
        return toResponse(code, null);
    }

    private ResponseEntity<ReservationTableAssignmentApiErrorResponse> toResponse(
        ReservationTableAssignmentApiErrorCode code,
        ApiIdempotencyResponse idempotency
    ) {
        return ResponseEntity.status(code.httpStatus()).body(
            ReservationTableAssignmentApiErrorResponse.of(code, idempotency)
        );
    }

    private static ApiIdempotencyResponse idempotencyFor(ReservationTableAssignmentApiErrorCode code) {
        if (code == ReservationTableAssignmentApiErrorCode.COMMAND_IN_PROGRESS) {
            return ApiIdempotencyResponse.started();
        }
        if (code == ReservationTableAssignmentApiErrorCode.IDEMPOTENCY_CONFLICT) {
            return ApiIdempotencyResponse.conflict();
        }
        return ApiIdempotencyResponse.failed();
    }

    private static ReservationTableAssignmentApiErrorCode toApiErrorCode(ReservationTableAssignmentError error) {
        return switch (error) {
            case INVALID_COMMAND -> ReservationTableAssignmentApiErrorCode.INVALID_COMMAND;
            case MISSING_IDEMPOTENCY_KEY -> ReservationTableAssignmentApiErrorCode.MISSING_IDEMPOTENCY_KEY;
            case FORBIDDEN -> ReservationTableAssignmentApiErrorCode.FORBIDDEN;
            case STORE_NOT_FOUND -> ReservationTableAssignmentApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> ReservationTableAssignmentApiErrorCode.STORE_SCOPE_MISMATCH;
            case RESERVATION_NOT_FOUND -> ReservationTableAssignmentApiErrorCode.RESERVATION_NOT_FOUND;
            case TABLE_NOT_FOUND -> ReservationTableAssignmentApiErrorCode.TABLE_NOT_FOUND;
            case RESERVATION_NOT_ASSIGNABLE -> ReservationTableAssignmentApiErrorCode.RESERVATION_NOT_ASSIGNABLE;
            case RESERVATION_ALREADY_ASSIGNED -> ReservationTableAssignmentApiErrorCode.RESERVATION_ALREADY_ASSIGNED;
            case TABLE_CAPACITY_INSUFFICIENT -> ReservationTableAssignmentApiErrorCode.TABLE_CAPACITY_INSUFFICIENT;
            case TABLE_NOT_AVAILABLE -> ReservationTableAssignmentApiErrorCode.TABLE_NOT_AVAILABLE;
            case IDEMPOTENCY_CONFLICT -> ReservationTableAssignmentApiErrorCode.IDEMPOTENCY_CONFLICT;
            case COMMAND_IN_PROGRESS -> ReservationTableAssignmentApiErrorCode.COMMAND_IN_PROGRESS;
            case FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY -> ReservationTableAssignmentApiErrorCode.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY;
            case PERSISTENCE_ERROR -> ReservationTableAssignmentApiErrorCode.PERSISTENCE_ERROR;
            case BUSINESS_EVENT_WRITE_FAILED -> ReservationTableAssignmentApiErrorCode.BUSINESS_EVENT_WRITE_FAILED;
            case AUDIT_WRITE_FAILED -> ReservationTableAssignmentApiErrorCode.AUDIT_WRITE_FAILED;
        };
    }
}
