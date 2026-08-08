package com.rpb.reservation.payment.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentReferencePattern {
    private static final Pattern SYSTEM_REFERENCE = Pattern.compile(
        "\\b([A-Z0-9]{2,8}-\\d{6}-\\d{3,6}(?:-[A-Z0-9]{3,8})?)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private PaymentReferencePattern() {
    }

    public static Optional<String> extractSystemReference(String rawText) {
        String normalizedText = normalizeForSearch(rawText);
        Matcher matcher = SYSTEM_REFERENCE.matcher(normalizedText);
        while (matcher.find()) {
            String candidate = normalize(matcher.group(1));
            if (!candidate.isBlank()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public static String normalize(String value) {
        String text = value == null ? "" : value.trim().toUpperCase();
        text = normalizeHyphens(text);
        text = text.replaceAll("\\s*-\\s*", "-");
        text = text.replaceAll("\\s+", "");
        text = text.replaceAll("^[^A-Z0-9]+|[^A-Z0-9]+$", "");
        return text;
    }

    private static String normalizeForSearch(String value) {
        String text = value == null ? "" : value.toUpperCase();
        text = normalizeHyphens(text);
        return text.replaceAll("\\s*-\\s*", "-");
    }

    private static String normalizeHyphens(String value) {
        return value.replace('\u2010', '-')
            .replace('\u2011', '-')
            .replace('\u2012', '-')
            .replace('\u2013', '-')
            .replace('\u2014', '-');
    }
}
