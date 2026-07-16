package com.rpb.reservation.staffhome.api;

import java.util.Map;

public record StaffHomeOverviewApiErrorResponse(
    boolean success,
    ErrorBody error
) {

    public static StaffHomeOverviewApiErrorResponse of(StaffHomeOverviewApiErrorCode code) {
        return new StaffHomeOverviewApiErrorResponse(
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
