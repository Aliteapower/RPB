package com.rpb.reservation.payment.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentReferencePattern {
    private static final String OCR_SAFE_CHECK = "[ACDEFGHJKMNPQRTVWXY]{4}";
    private static final Pattern COMPACT_SYSTEM_REFERENCE = Pattern.compile(
        "\\b([A-Z0-9]{2,8}\\d{6}\\d{4}" + OCR_SAFE_CHECK + ")\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMPACT_SYSTEM_REFERENCE_WITH_OCR_SEPARATORS = Pattern.compile(
        "\\b([A-Z0-9]{2,8}(?:\\s*[-.]?\\s*)\\d{6}(?:\\s*[-.]?\\s*)\\d{4}(?:\\s*[-.]?\\s*)[ACDEFGHJKMNPQRTVWXY](?:\\s*[ACDEFGHJKMNPQRTVWXY]){3})\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SYSTEM_REFERENCE = Pattern.compile(
        "\\b([A-Z0-9]{2,8}-\\d{6}-\\d{3,6}(?:-[A-Z0-9]{3,8})?)\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SYSTEM_REFERENCE_WITH_OCR_SPACES = Pattern.compile(
        "\\b([A-Z0-9]{2,8}\\s*[-.]\\s*\\d{6}\\s*[-.]\\s*\\d(?:\\s*\\d){2,5}(?:\\s*[-.]\\s*[A-Z0-9](?:\\s*[A-Z0-9]){2,7})?)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private PaymentReferencePattern() {
    }

    public static Optional<String> extractSystemReference(String rawText) {
        String normalizedText = normalizeForSearch(rawText);
        return extractWith(COMPACT_SYSTEM_REFERENCE_WITH_OCR_SEPARATORS, normalizedText)
            .or(() -> extractWith(COMPACT_SYSTEM_REFERENCE, normalizedText))
            .or(() -> extractWith(SYSTEM_REFERENCE_WITH_OCR_SPACES, normalizedText))
            .or(() -> extractWith(SYSTEM_REFERENCE, normalizedText));
    }

    private static Optional<String> extractWith(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
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
        String separatorNormalized = text.replaceAll("\\s*[-.]\\s*", "-").replaceAll("\\s+", "");
        separatorNormalized = separatorNormalized.replaceAll("^[^A-Z0-9]+|[^A-Z0-9]+$", "");
        String compactCandidate = separatorNormalized.replace("-", "");
        if (COMPACT_SYSTEM_REFERENCE.matcher(compactCandidate).matches()) {
            return compactCandidate;
        }
        return separatorNormalized;
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
