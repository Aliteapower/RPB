package com.rpb.reservation.customer.api;

import java.util.Map;

public record CustomerPhoneLookupApiErrorResponse(
    boolean success,
    ErrorBody error
) {
    public static CustomerPhoneLookupApiErrorResponse of(CustomerPhoneLookupApiErrorCode code) {
        return new CustomerPhoneLookupApiErrorResponse(
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
