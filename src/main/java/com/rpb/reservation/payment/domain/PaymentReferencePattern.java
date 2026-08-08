package com.rpb.reservation.payment.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentReferencePattern {
    private static final String OCR_SAFE_CHECK = "[ACDEFGHJKMNPQRTVWXY]{4}";
    private static final Pattern COMPACT_SYSTEM_REFERENCE = Pattern.compile(
        "\\b([A-Z0-9]{2,8}\\d{6}\\d{4}" + OCR_SAFE_CHECK + ")\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMPACT_REFERENCE_SEGMENTS = Pattern.compile(
        "^([A-Z0-9]{2,8})-?(\\d{6})-?(\\d{4})-?(" + OCR_SAFE_CHECK + ")$",
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
        "\\b([A-Z0-9]{2,8}(?:\\s*[-.]\\s*|\\s+)\\d{6}(?:\\s*[-.]\\s*|\\s+)\\d(?:\\s*\\d){2,5}(?:(?:\\s*[-.]\\s*|\\s+)[A-Z0-9](?:\\s*[A-Z0-9]){2,7})?)\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LEGACY_SYSTEM_REFERENCE_WITH_SPACES = Pattern.compile(
        "^([A-Z0-9]{2,8})\\s+(\\d{6})\\s+(\\d(?:\\s*\\d){2,5})(?:\\s+([A-Z0-9](?:\\s*[A-Z0-9]){2,7}))?$",
        Pattern.CASE_INSENSITIVE
    );

    private PaymentReferencePattern() {
    }

    public static Optional<String> extractSystemReference(String rawText) {
        String normalizedText = normalizeForSearch(rawText);
        return extractWith(
            COMPACT_SYSTEM_REFERENCE_WITH_OCR_SEPARATORS,
            normalizedText,
            PaymentReferencePattern::isValidCompactCandidate
        )
            .or(() -> extractWith(COMPACT_SYSTEM_REFERENCE, normalizedText, PaymentReferencePattern::isValidCompactCandidate))
            .or(() -> extractWith(SYSTEM_REFERENCE_WITH_OCR_SPACES, normalizedText))
            .or(() -> extractWith(SYSTEM_REFERENCE, normalizedText));
    }

    private static Optional<String> extractWith(Pattern pattern, String text) {
        return extractWith(pattern, text, candidate -> true);
    }

    private static Optional<String> extractWith(Pattern pattern, String text, Predicate<String> validator) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String candidate = normalize(matcher.group(1));
            if (!candidate.isBlank() && validator.test(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public static List<String> lookupVariants(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        Set<String> variants = new LinkedHashSet<>();
        variants.add(normalized);
        String compact = normalized.replace("-", "");
        Matcher matcher = COMPACT_REFERENCE_SEGMENTS.matcher(compact);
        if (matcher.matches()) {
            variants.add(compact);
            variants.add(String.join("-", matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4)));
        }
        return List.copyOf(variants);
    }

    public static boolean matches(String first, String second) {
        Set<String> firstVariants = Set.copyOf(lookupVariants(first));
        return lookupVariants(second).stream().anyMatch(firstVariants::contains);
    }

    public static String normalize(String value) {
        String text = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        text = normalizeHyphens(text);
        boolean hasExplicitHyphen = text.contains("-");
        String separatorNormalized = text.replaceAll("\\s*[-.]\\s*", "-").replaceAll("\\s+", "");
        separatorNormalized = separatorNormalized.replaceAll("^[^A-Z0-9]+|[^A-Z0-9]+$", "");
        String compactCandidate = separatorNormalized.replace("-", "");
        if (!hasExplicitHyphen && COMPACT_SYSTEM_REFERENCE.matcher(compactCandidate).matches()) {
            return compactCandidate;
        }
        Matcher legacyWithSpaces = LEGACY_SYSTEM_REFERENCE_WITH_SPACES.matcher(text);
        if (legacyWithSpaces.matches()) {
            String normalized = legacyWithSpaces.group(1) + "-" + legacyWithSpaces.group(2) + "-"
                + legacyWithSpaces.group(3).replaceAll("\\s+", "");
            if (legacyWithSpaces.group(4) != null) {
                normalized += "-" + legacyWithSpaces.group(4).replaceAll("\\s+", "");
            }
            return normalized;
        }
        return separatorNormalized;
    }

    private static String normalizeForSearch(String value) {
        String text = value == null ? "" : value.toUpperCase(Locale.ROOT);
        text = normalizeHyphens(text);
        return text.replaceAll("\\s*-\\s*", "-");
    }

    private static boolean isValidCompactCandidate(String candidate) {
        return PaymentReferenceGenerator.isValidCompactReference(candidate.replace("-", ""));
    }

    private static String normalizeHyphens(String value) {
        return value.replace('\u2010', '-')
            .replace('\u2011', '-')
            .replace('\u2012', '-')
            .replace('\u2013', '-')
            .replace('\u2014', '-');
    }
}
