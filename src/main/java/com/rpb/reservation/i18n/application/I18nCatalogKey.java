package com.rpb.reservation.i18n.application;

import java.util.List;
import java.util.Objects;

public record I18nCatalogKey(
    String i18nKey,
    String namespace,
    String category,
    String displayName,
    String description,
    String textKind,
    boolean tenantEditable,
    List<String> placeholderNames,
    String status,
    int sortOrder
) {
    public I18nCatalogKey {
        requireText(i18nKey, "i18n_key_required");
        requireText(namespace, "i18n_namespace_required");
        requireText(category, "i18n_category_required");
        requireText(displayName, "i18n_display_name_required");
        requireText(textKind, "i18n_text_kind_required");
        requireText(status, "i18n_key_status_required");
        placeholderNames = List.copyOf(Objects.requireNonNullElse(placeholderNames, List.of()));
        description = clean(description);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
