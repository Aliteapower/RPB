package com.rpb.reservation.table.api;

import com.rpb.reservation.table.application.TableResourceListError;
import com.rpb.reservation.table.application.TableResourceListResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class TableResourceListApiErrorMapper {

    public ResponseEntity<TableResourceListApiErrorResponse> toResponse(TableResourceListResult result) {
        return toResponse(toApiErrorCode(result.error()));
    }

    public ResponseEntity<TableResourceListApiErrorResponse> toResponse(TableResourceListApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(TableResourceListApiErrorResponse.of(code));
    }

    private TableResourceListApiErrorCode toApiErrorCode(TableResourceListError error) {
        return switch (error) {
            case INVALID_STATUS -> TableResourceListApiErrorCode.INVALID_STATUS;
            case INVALID_PARTY_SIZE -> TableResourceListApiErrorCode.INVALID_PARTY_SIZE;
            case STORE_SCOPE_MISMATCH -> TableResourceListApiErrorCode.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> TableResourceListApiErrorCode.FORBIDDEN;
            case PERSISTENCE_ERROR -> TableResourceListApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
