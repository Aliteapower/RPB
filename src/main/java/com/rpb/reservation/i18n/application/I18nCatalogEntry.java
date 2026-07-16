package com.rpb.reservation.i18n.application;

import java.util.List;

public record I18nCatalogEntry(
    I18nCatalogKey key,
    List<I18nCatalogLocaleView> locales
) {
    public I18nCatalogEntry {
        locales = List.copyOf(locales);
    }
}
