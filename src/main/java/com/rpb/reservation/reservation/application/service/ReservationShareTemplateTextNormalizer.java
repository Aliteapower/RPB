package com.rpb.reservation.reservation.application.service;

public final class ReservationShareTemplateTextNormalizer {
    private ReservationShareTemplateTextNormalizer() {
    }

    public static String normalize(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.trim()
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\r", "\n");
    }

    public static String optional(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
