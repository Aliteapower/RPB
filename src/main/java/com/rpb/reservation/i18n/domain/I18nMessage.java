package com.rpb.reservation.i18n.domain;

import com.rpb.reservation.common.value.I18nKey;
import java.util.Objects;
import java.util.UUID;

/**
 * I18nMessage domain skeleton. It carries display text for a key and locale,
 * not business rule decisions.
 */
public record I18nMessage(UUID id, I18nKey i18nKey, String locale, String message, String status) {

    public I18nMessage {
        Objects.requireNonNull(id, "i18n_message_id_required");
        Objects.requireNonNull(i18nKey, "i18n_key_required");
        requireText(locale, "locale_required");
        requireText(message, "message_required");
        requireText(status, "i18n_message_status_required");
    }

    public String resolveIntent() {
        return "i18n_message.resolve.intent";
    }

    public String domainBoundary() {
        return "I18nMessage resolves display text and does not decide business state.";
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
