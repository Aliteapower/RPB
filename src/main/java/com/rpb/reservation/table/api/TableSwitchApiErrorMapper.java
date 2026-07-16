package com.rpb.reservation.table.api;

import com.rpb.reservation.table.application.TableSwitchError;
import com.rpb.reservation.table.application.TableSwitchResult;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class TableSwitchApiErrorMapper {

    public ResponseEntity<TableSwitchApiErrorResponse> toResponse(TableSwitchResult result) {
        TableSwitchApiErrorCode code = toApiErrorCode(result.error());
        return toResponse(code, idempotencyFor(code));
    }

    public ResponseEntity<TableSwitchApiErrorResponse> toResponse(TableSwitchApiErrorCode code) {
        return toResponse(code, idempotencyFor(code));
    }

    private ResponseEntity<TableSwitchApiErrorResponse> toResponse(TableSwitchApiErrorCode code, ApiIdempotencyResponse idempotency) {
        return ResponseEntity.status(code.httpStatus()).body(TableSwitchApiErrorResponse.of(code, idempotency));
    }

    private ApiIdempotencyResponse idempotencyFor(TableSwitchApiErrorCode code) {
        if (code == TableSwitchApiErrorCode.IDEMPOTENCY_IN_PROGRESS) {
            return ApiIdempotencyResponse.started();
        }
        if (code == TableSwitchApiErrorCode.IDEMPOTENCY_CONFLICT) {
            return ApiIdempotencyResponse.conflict();
        }
        return ApiIdempotencyResponse.failed();
    }

    private TableSwitchApiErrorCode toApiErrorCode(TableSwitchError error) {
        return switch (error) {
            case INVALID_COMMAND, TARGET_REQUIRED, TARGET_AMBIGUOUS -> TableSwitchApiErrorCode.TABLE_SWITCH_TARGET_INVALID;
            case STORE_NOT_FOUND -> TableSwitchApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> TableSwitchApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> TableSwitchApiErrorCode.FORBIDDEN;
            case SEATING_NOT_FOUND -> TableSwitchApiErrorCode.SEATING_NOT_FOUND;
            case SEATING_NOT_OCCUPIED -> TableSwitchApiErrorCode.SEATING_NOT_OCCUPIED;
            case ACTIVE_SEATING_RESOURCE_NOT_FOUND -> TableSwitchApiErrorCode.ACTIVE_SEATING_RESOURCE_NOT_FOUND;
            case TARGET_SAME_AS_CURRENT -> TableSwitchApiErrorCode.TABLE_SWITCH_TARGET_SAME_AS_CURRENT;
            case TABLE_NOT_FOUND -> TableSwitchApiErrorCode.TABLE_NOT_FOUND;
            case TABLE_GROUP_NOT_FOUND -> TableSwitchApiErrorCode.TABLE_GROUP_NOT_FOUND;
            case TABLE_NOT_AVAILABLE -> TableSwitchApiErrorCode.TABLE_NOT_AVAILABLE;
            case TABLE_GROUP_INVALID -> TableSwitchApiErrorCode.TABLE_GROUP_INVALID;
            case TABLE_CAPACITY_INSUFFICIENT -> TableSwitchApiErrorCode.TABLE_CAPACITY_INSUFFICIENT;
            case TABLE_GROUP_CAPACITY_INSUFFICIENT -> TableSwitchApiErrorCode.TABLE_GROUP_CAPACITY_INSUFFICIENT;
            case TABLE_LOCK_CONFLICT -> TableSwitchApiErrorCode.TABLE_LOCK_CONFLICT;
            case TABLE_RESOURCE_UNAVAILABLE -> TableSwitchApiErrorCode.TABLE_RESOURCE_UNAVAILABLE;
            case CLEANING_ALREADY_ACTIVE -> TableSwitchApiErrorCode.CLEANING_ALREADY_ACTIVE;
            case IDEMPOTENCY_CONFLICT -> TableSwitchApiErrorCode.IDEMPOTENCY_CONFLICT;
            case COMMAND_IN_PROGRESS -> TableSwitchApiErrorCode.IDEMPOTENCY_IN_PROGRESS;
            case FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY -> TableSwitchApiErrorCode.IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY;
            case ILLEGAL_STATE_TRANSITION -> TableSwitchApiErrorCode.ILLEGAL_STATE_TRANSITION;
            case AUDIT_WRITE_FAILED -> TableSwitchApiErrorCode.AUDIT_WRITE_FAILED;
            case BUSINESS_EVENT_WRITE_FAILED, STATE_TRANSITION_WRITE_FAILED, REPOSITORY_SAVE_FAILED -> TableSwitchApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
