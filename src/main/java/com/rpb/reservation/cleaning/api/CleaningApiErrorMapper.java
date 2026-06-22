package com.rpb.reservation.cleaning.api;

import com.rpb.reservation.cleaning.application.CleaningApplicationError;
import com.rpb.reservation.cleaning.application.CleaningApplicationResult;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class CleaningApiErrorMapper {

    public ResponseEntity<CleaningApiErrorResponse> toResponse(CleaningApplicationResult result) {
        CleaningApiErrorCode code = toApiErrorCode(result.error());
        return toResponse(code, idempotencyFor(code));
    }

    public ResponseEntity<CleaningApiErrorResponse> toResponse(CleaningApiErrorCode code) {
        return toResponse(code, idempotencyFor(code));
    }

    private ResponseEntity<CleaningApiErrorResponse> toResponse(CleaningApiErrorCode code, ApiIdempotencyResponse idempotency) {
        return ResponseEntity.status(code.httpStatus()).body(CleaningApiErrorResponse.of(code, idempotency));
    }

    private ApiIdempotencyResponse idempotencyFor(CleaningApiErrorCode code) {
        if (code == CleaningApiErrorCode.IDEMPOTENCY_IN_PROGRESS) {
            return ApiIdempotencyResponse.started();
        }
        if (code == CleaningApiErrorCode.IDEMPOTENCY_CONFLICT) {
            return ApiIdempotencyResponse.conflict();
        }
        return ApiIdempotencyResponse.failed();
    }

    private CleaningApiErrorCode toApiErrorCode(CleaningApplicationError error) {
        return switch (error) {
            case INVALID_COMMAND, RESOURCE_TARGET_INVALID -> CleaningApiErrorCode.CLEANING_TARGET_INVALID;
            case STORE_NOT_FOUND -> CleaningApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> CleaningApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> CleaningApiErrorCode.FORBIDDEN;
            case SEATING_NOT_FOUND -> CleaningApiErrorCode.SEATING_NOT_FOUND;
            case SEATING_RESOURCE_NOT_FOUND -> CleaningApiErrorCode.SEATING_RESOURCE_NOT_FOUND;
            case TABLE_NOT_FOUND -> CleaningApiErrorCode.TABLE_NOT_FOUND;
            case INVALID_TABLE_GROUP -> CleaningApiErrorCode.TABLE_GROUP_INVALID;
            case TABLE_NOT_OCCUPIED -> CleaningApiErrorCode.TABLE_NOT_OCCUPIED;
            case CLEANING_ALREADY_ACTIVE -> CleaningApiErrorCode.CLEANING_ALREADY_ACTIVE;
            case CLEANING_NOT_FOUND -> CleaningApiErrorCode.CLEANING_NOT_FOUND;
            case CLEANING_ALREADY_COMPLETED -> CleaningApiErrorCode.CLEANING_ALREADY_COMPLETED;
            case TABLE_NOT_CLEANING -> CleaningApiErrorCode.TABLE_NOT_CLEANING;
            case IDEMPOTENCY_CONFLICT -> CleaningApiErrorCode.IDEMPOTENCY_CONFLICT;
            case COMMAND_IN_PROGRESS -> CleaningApiErrorCode.IDEMPOTENCY_IN_PROGRESS;
            case FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY -> CleaningApiErrorCode.IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY;
            case ILLEGAL_STATE_TRANSITION -> CleaningApiErrorCode.ILLEGAL_STATE_TRANSITION;
            case AUDIT_WRITE_FAILED -> CleaningApiErrorCode.AUDIT_WRITE_FAILED;
            case BUSINESS_EVENT_WRITE_FAILED, STATE_TRANSITION_WRITE_FAILED, REPOSITORY_SAVE_FAILED -> CleaningApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
