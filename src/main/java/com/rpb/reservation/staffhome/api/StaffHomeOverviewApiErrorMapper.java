package com.rpb.reservation.staffhome.api;

import com.rpb.reservation.staffhome.application.StaffHomeOverviewError;
import com.rpb.reservation.staffhome.application.StaffHomeOverviewResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class StaffHomeOverviewApiErrorMapper {

    public ResponseEntity<StaffHomeOverviewApiErrorResponse> toResponse(StaffHomeOverviewResult result) {
        return toResponse(toApiErrorCode(result.error()));
    }

    public ResponseEntity<StaffHomeOverviewApiErrorResponse> toResponse(StaffHomeOverviewApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(StaffHomeOverviewApiErrorResponse.of(code));
    }

    private static StaffHomeOverviewApiErrorCode toApiErrorCode(StaffHomeOverviewError error) {
        return switch (error) {
            case INVALID_QUERY -> StaffHomeOverviewApiErrorCode.INVALID_QUERY;
            case INVALID_BUSINESS_DATE -> StaffHomeOverviewApiErrorCode.INVALID_BUSINESS_DATE;
            case STORE_NOT_FOUND -> StaffHomeOverviewApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> StaffHomeOverviewApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> StaffHomeOverviewApiErrorCode.FORBIDDEN;
            case PERSISTENCE_ERROR -> StaffHomeOverviewApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
