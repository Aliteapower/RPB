package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationCalendarSummaryResult;
import com.rpb.reservation.reservation.application.ReservationShareInfoError;
import com.rpb.reservation.reservation.application.ReservationShareInfoResult;
import com.rpb.reservation.reservation.application.ReservationShareIntentResult;
import com.rpb.reservation.reservation.application.ReservationTodayViewError;
import com.rpb.reservation.reservation.application.ReservationTodayViewResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ReservationTodayViewApiErrorMapper {

    public ResponseEntity<ReservationTodayViewApiErrorResponse> toResponse(ReservationTodayViewResult result) {
        return toResponse(toApiErrorCode(result.error()));
    }

    public ResponseEntity<ReservationTodayViewApiErrorResponse> toResponse(ReservationCalendarSummaryResult result) {
        return toResponse(toApiErrorCode(result.error()));
    }

    public ResponseEntity<ReservationTodayViewApiErrorResponse> toResponse(ReservationShareInfoResult result) {
        return toResponse(toApiErrorCode(result.error()));
    }

    public ResponseEntity<ReservationTodayViewApiErrorResponse> toResponse(ReservationShareIntentResult result) {
        return toResponse(toApiErrorCode(result.error()));
    }

    public ResponseEntity<ReservationTodayViewApiErrorResponse> toResponse(ReservationApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(ReservationTodayViewApiErrorResponse.of(code));
    }

    private static ReservationApiErrorCode toApiErrorCode(ReservationTodayViewError error) {
        return switch (error) {
            case INVALID_COMMAND -> ReservationApiErrorCode.INVALID_COMMAND;
            case STORE_NOT_FOUND -> ReservationApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> ReservationApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> ReservationApiErrorCode.FORBIDDEN;
            case INVALID_BUSINESS_DATE -> ReservationApiErrorCode.INVALID_BUSINESS_DATE;
            case INVALID_STATUS_FILTER -> ReservationApiErrorCode.INVALID_STATUS_FILTER;
            case PERSISTENCE_ERROR -> ReservationApiErrorCode.PERSISTENCE_ERROR;
        };
    }

    private static ReservationApiErrorCode toApiErrorCode(ReservationShareInfoError error) {
        return switch (error) {
            case INVALID_COMMAND -> ReservationApiErrorCode.INVALID_COMMAND;
            case STORE_NOT_FOUND -> ReservationApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> ReservationApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> ReservationApiErrorCode.FORBIDDEN;
            case RESERVATION_NOT_FOUND -> ReservationApiErrorCode.RESERVATION_NOT_FOUND;
            case PERSISTENCE_ERROR -> ReservationApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
