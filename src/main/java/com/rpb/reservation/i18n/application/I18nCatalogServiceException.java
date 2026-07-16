package com.rpb.reservation.i18n.application;

public class I18nCatalogServiceException extends RuntimeException {
    private final I18nCatalogServiceErrorCode code;

    public I18nCatalogServiceException(I18nCatalogServiceErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public I18nCatalogServiceErrorCode code() {
        return code;
    }
}
