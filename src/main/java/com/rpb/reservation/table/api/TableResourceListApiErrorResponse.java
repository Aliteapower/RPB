package com.rpb.reservation.table.api;

import java.util.Map;

public record TableResourceListApiErrorResponse(
    boolean success,
    ErrorBody error
) {

    public static TableResourceListApiErrorResponse of(TableResourceListApiErrorCode code) {
        return new TableResourceListApiErrorResponse(
            false,
            new ErrorBody(code.name(), code.messageKey(), Map.of())
        );
    }

    public record ErrorBody(
        String code,
        String messageKey,
        Map<String, Object> details
    ) {
    }
}
