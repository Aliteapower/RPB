package com.rpb.reservation.queue.api;

import com.rpb.reservation.queue.application.QueueTicketListError;
import com.rpb.reservation.queue.application.QueueTicketListResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class QueueTicketListApiErrorMapper {

    public ResponseEntity<QueueTicketListApiErrorResponse> toResponse(QueueTicketListResult result) {
        return toResponse(toApiErrorCode(result.error()));
    }

    public ResponseEntity<QueueTicketListApiErrorResponse> toResponse(QueueTicketListApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(QueueTicketListApiErrorResponse.of(code));
    }

    private QueueTicketListApiErrorCode toApiErrorCode(QueueTicketListError error) {
        return switch (error) {
            case INVALID_QUERY -> QueueTicketListApiErrorCode.INVALID_QUERY;
            case INVALID_STATUS -> QueueTicketListApiErrorCode.INVALID_STATUS;
            case INVALID_LIMIT -> QueueTicketListApiErrorCode.INVALID_LIMIT;
            case INVALID_OFFSET -> QueueTicketListApiErrorCode.INVALID_OFFSET;
            case STORE_NOT_FOUND -> QueueTicketListApiErrorCode.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> QueueTicketListApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> QueueTicketListApiErrorCode.FORBIDDEN;
            case PERSISTENCE_ERROR -> QueueTicketListApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
