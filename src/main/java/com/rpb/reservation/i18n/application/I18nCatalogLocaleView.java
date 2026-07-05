package com.rpb.reservation.i18n.application;

public record I18nCatalogLocaleView(
    String locale,
    I18nCatalogStoredMessage platformMessage,
    I18nCatalogStoredMessage tenantOverride,
    I18nCatalogStoredMessage storeOverride,
    String effectiveMessage,
    String effectiveSource
) {
}
