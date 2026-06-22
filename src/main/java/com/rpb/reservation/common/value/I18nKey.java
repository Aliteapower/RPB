package com.rpb.reservation.common.value;

/**
 * Stable i18n message key. Display text must be resolved later by i18n.
 */
public record I18nKey(String value) {

    public I18nKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("i18n_key_required");
        }
    }
}
