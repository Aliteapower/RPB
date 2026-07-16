package com.rpb.reservation.table.api;

import java.util.Map;

public record TemporaryTableGroupApiErrorResponse(
    boolean success,
    ErrorBody error
) {

    public static TemporaryTableGroupApiErrorResponse of(TemporaryTableGroupApiErrorCode code) {
        return new TemporaryTableGroupApiErrorResponse(
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
