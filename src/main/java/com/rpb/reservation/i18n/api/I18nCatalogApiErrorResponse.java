package com.rpb.reservation.i18n.api;

import java.util.Map;

public record I18nCatalogApiErrorResponse(
    boolean success,
    ErrorBody error
) {
    public static I18nCatalogApiErrorResponse of(I18nCatalogApiErrorCode code) {
        return new I18nCatalogApiErrorResponse(false, new ErrorBody(code.name(), code.messageKey(), Map.of()));
    }

    public record ErrorBody(
        String code,
        String messageKey,
        Map<String, Object> details
    ) {
    }
}
