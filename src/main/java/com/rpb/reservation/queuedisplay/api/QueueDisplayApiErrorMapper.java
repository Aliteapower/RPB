package com.rpb.reservation.queuedisplay.api;

import com.rpb.reservation.queuedisplay.application.QueueDisplayError;
import com.rpb.reservation.queuedisplay.application.QueueDisplayResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class QueueDisplayApiErrorMapper {
    public ResponseEntity<QueueDisplayApiErrorResponse> toResponse(QueueDisplayApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(QueueDisplayApiErrorResponse.of(code));
    }

    public ResponseEntity<QueueDisplayApiErrorResponse> toResponse(QueueDisplayResult result) {
        return toResponse(toApiCode(result.error()));
    }

    private static QueueDisplayApiErrorCode toApiCode(QueueDisplayError error) {
        if (error == null) {
            return QueueDisplayApiErrorCode.PERSISTENCE_ERROR;
        }
        return switch (error) {
            case STORE_NOT_FOUND -> QueueDisplayApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> QueueDisplayApiErrorCode.STORE_SCOPE_MISMATCH;
            case QUEUE_DISPLAY_CONFIG_INVALID -> QueueDisplayApiErrorCode.QUEUE_DISPLAY_CONFIG_INVALID;
            case FORBIDDEN, INVALID_QUERY -> QueueDisplayApiErrorCode.FORBIDDEN;
            case PERSISTENCE_ERROR -> QueueDisplayApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
