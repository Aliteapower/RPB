package com.rpb.reservation.table.api;

import com.rpb.reservation.table.application.TemporaryTableGroupError;
import com.rpb.reservation.table.application.TemporaryTableGroupResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class TemporaryTableGroupApiErrorMapper {

    public ResponseEntity<TemporaryTableGroupApiErrorResponse> toResponse(TemporaryTableGroupResult result) {
        return toResponse(toApiErrorCode(result.error()));
    }

    public ResponseEntity<TemporaryTableGroupApiErrorResponse> toResponse(TemporaryTableGroupApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(TemporaryTableGroupApiErrorResponse.of(code));
    }

    private TemporaryTableGroupApiErrorCode toApiErrorCode(TemporaryTableGroupError error) {
        return switch (error) {
            case GROUP_NAME_REQUIRED -> TemporaryTableGroupApiErrorCode.GROUP_NAME_REQUIRED;
            case GROUP_NAME_CONFLICT -> TemporaryTableGroupApiErrorCode.GROUP_NAME_CONFLICT;
            case GROUP_NOT_FOUND -> TemporaryTableGroupApiErrorCode.GROUP_NOT_FOUND;
            case GROUP_NOT_TEMPORARY -> TemporaryTableGroupApiErrorCode.GROUP_NOT_TEMPORARY;
            case GROUP_NOT_DISSOLVABLE -> TemporaryTableGroupApiErrorCode.GROUP_NOT_DISSOLVABLE;
            case MEMBER_REQUIRED -> TemporaryTableGroupApiErrorCode.MEMBER_REQUIRED;
            case MEMBER_DUPLICATE -> TemporaryTableGroupApiErrorCode.MEMBER_DUPLICATE;
            case MEMBER_UNAVAILABLE -> TemporaryTableGroupApiErrorCode.MEMBER_UNAVAILABLE;
            case CAPACITY_INSUFFICIENT -> TemporaryTableGroupApiErrorCode.CAPACITY_INSUFFICIENT;
            case LOCK_CONFLICT -> TemporaryTableGroupApiErrorCode.LOCK_CONFLICT;
            case PREASSIGNMENT_CONFLICT -> TemporaryTableGroupApiErrorCode.PREASSIGNMENT_CONFLICT;
        };
    }
}
