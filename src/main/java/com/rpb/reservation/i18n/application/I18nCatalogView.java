package com.rpb.reservation.i18n.application;

import java.util.List;

public record I18nCatalogView(
    List<String> supportedLocales,
    List<I18nCatalogEntry> entries
) {
    public I18nCatalogView {
        supportedLocales = List.copyOf(supportedLocales);
        entries = List.copyOf(entries);
    }
}
