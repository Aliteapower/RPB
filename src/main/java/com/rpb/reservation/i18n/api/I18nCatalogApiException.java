package com.rpb.reservation.i18n.api;

public class I18nCatalogApiException extends RuntimeException {
    private final I18nCatalogApiErrorCode code;

    public I18nCatalogApiException(I18nCatalogApiErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public I18nCatalogApiErrorCode code() {
        return code;
    }
}
