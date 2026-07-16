package com.rpb.reservation.i18n.application;

import java.util.Objects;
import java.util.UUID;

public record I18nCatalogStoredMessage(
    UUID id,
    String i18nKey,
    String locale,
    String message,
    String status,
    int version
) {
    public I18nCatalogStoredMessage {
        Objects.requireNonNull(id, "i18n_message_id_required");
        requireText(i18nKey, "i18n_key_required");
        requireText(locale, "locale_required");
        requireText(message, "message_required");
        requireText(status, "i18n_message_status_required");
    }

    public boolean active() {
        return "active".equals(status);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
