package com.rpb.reservation.walkin.api;

import com.rpb.reservation.walkin.application.WalkInDirectSeatingError;
import com.rpb.reservation.walkin.application.WalkInDirectSeatingResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class WalkInDirectSeatingApiErrorMapper {

    public ResponseEntity<ApiErrorResponse> toResponse(WalkInDirectSeatingResult result) {
        ApiErrorCode code = toApiErrorCode(result.error());
        return toResponse(code, idempotencyFor(code));
    }

    public ResponseEntity<ApiErrorResponse> toResponse(ApiErrorCode code) {
        return toResponse(code, idempotencyFor(code));
    }

    private ResponseEntity<ApiErrorResponse> toResponse(ApiErrorCode code, ApiIdempotencyResponse idempotency) {
        return ResponseEntity.status(code.httpStatus()).body(ApiErrorResponse.of(code, idempotency));
    }

    private ApiIdempotencyResponse idempotencyFor(ApiErrorCode code) {
        if (code == ApiErrorCode.IDEMPOTENCY_IN_PROGRESS) {
            return ApiIdempotencyResponse.started();
        }
        if (code == ApiErrorCode.IDEMPOTENCY_CONFLICT) {
            return ApiIdempotencyResponse.conflict();
        }
        return ApiIdempotencyResponse.failed();
    }

    private ApiErrorCode toApiErrorCode(WalkInDirectSeatingError error) {
        return switch (error) {
            case STORE_NOT_FOUND -> ApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> ApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> ApiErrorCode.FORBIDDEN;
            case INVALID_PARTY_SIZE -> ApiErrorCode.INVALID_PARTY_SIZE;
            case INVALID_CUSTOMER_IDENTITY -> ApiErrorCode.INVALID_CUSTOMER_IDENTITY;
            case INVALID_RESOURCE_SELECTION, INVALID_COMMAND -> ApiErrorCode.RESOURCE_CONFLICT;
            case NO_ASSIGNABLE_TABLE, TABLE_RESOURCE_UNAVAILABLE -> ApiErrorCode.TABLE_NOT_AVAILABLE;
            case PARTY_SIZE_OUTSIDE_CAPACITY -> ApiErrorCode.TABLE_CAPACITY_INSUFFICIENT;
            case TABLE_LOCK_CONFLICT -> ApiErrorCode.TABLE_LOCK_CONFLICT;
            case INVALID_TABLE_GROUP -> ApiErrorCode.TABLE_GROUP_INVALID;
            case TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED -> ApiErrorCode.TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED;
            case TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE -> ApiErrorCode.TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE;
            case TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE -> ApiErrorCode.TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE;
            case TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT -> ApiErrorCode.TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT;
            case TEMPORARY_TABLE_GROUP_LOCK_CONFLICT -> ApiErrorCode.TEMPORARY_TABLE_GROUP_LOCK_CONFLICT;
            case TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT -> ApiErrorCode.TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT;
            case MANUAL_OVERRIDE_REQUIRED -> ApiErrorCode.OVERRIDE_REASON_REQUIRED;
            case INVALID_SEATING_SOURCE -> ApiErrorCode.SEATING_SOURCE_INVALID;
            case INVALID_SEATING_RESOURCE -> ApiErrorCode.SEATING_RESOURCE_INVALID;
            case IDEMPOTENCY_CONFLICT -> ApiErrorCode.IDEMPOTENCY_CONFLICT;
            case COMMAND_IN_PROGRESS -> ApiErrorCode.IDEMPOTENCY_IN_PROGRESS;
            case FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY -> ApiErrorCode.IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY;
            case ILLEGAL_STATE_TRANSITION -> ApiErrorCode.ILLEGAL_STATE_TRANSITION;
            case AUDIT_WRITE_FAILED -> ApiErrorCode.AUDIT_WRITE_FAILED;
            case BUSINESS_EVENT_WRITE_FAILED, STATE_TRANSITION_WRITE_FAILED, REPOSITORY_SAVE_FAILED -> ApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
