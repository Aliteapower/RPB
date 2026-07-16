package com.rpb.reservation.tenantadmin.api;

import java.util.Map;

public record TenantAdminApiErrorResponse(
    boolean success,
    ErrorBody error
) {
    public static TenantAdminApiErrorResponse of(TenantAdminApiErrorCode code) {
        return new TenantAdminApiErrorResponse(
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
