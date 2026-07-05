package com.rpb.reservation.i18n.api;

import org.springframework.http.HttpStatus;

public enum I18nCatalogApiErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "i18n.catalog.unauthenticated"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "i18n.catalog.forbidden"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "i18n.catalog.request_invalid"),
    KEY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "i18n.catalog.key_not_allowed"),
    LOCALE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "i18n.catalog.locale_not_supported"),
    PLACEHOLDER_UNKNOWN(HttpStatus.BAD_REQUEST, "i18n.catalog.placeholder_unknown"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "i18n.catalog.version_conflict"),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "i18n.catalog.persistence_error");

    private final HttpStatus httpStatus;
    private final String messageKey;

    I18nCatalogApiErrorCode(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String messageKey() {
        return messageKey;
    }
}
