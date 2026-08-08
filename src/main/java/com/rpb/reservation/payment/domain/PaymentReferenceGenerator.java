package com.rpb.reservation.payment.domain;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public final class PaymentReferenceGenerator {
    private static final int MAX_SEQUENCE = 9999;
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("uuuuMM")
        .withResolverStyle(ResolverStyle.STRICT);
    private static final char[] CHECK_ALPHABET = "ACDEFGHJKMNPQRTVWXY".toCharArray();
    private static final Pattern COMPACT_REFERENCE = Pattern.compile(
        "([A-Z0-9]{2,8})(\\d{6})(\\d{4})([ACDEFGHJKMNPQRTVWXY]{4})"
    );

    private PaymentReferenceGenerator() {
    }

    public static String generate(String prefix, YearMonth period, int sequence) {
        String cleanPrefix = normalizePrefix(prefix);
        if (period == null || !isValidSequence(sequence)) {
            throw new IllegalArgumentException("payment_reference_sequence_invalid");
        }
        String sequenceText = "%04d".formatted(sequence);
        String base = cleanPrefix + period.format(PERIOD_FORMATTER) + sequenceText;
        return base + checksum(base);
    }

    public static boolean isValidSequence(int sequence) {
        return sequence > 0 && sequence <= MAX_SEQUENCE;
    }

    public static boolean isValidCompactReference(String value) {
        String reference = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = COMPACT_REFERENCE.matcher(reference);
        if (!matcher.matches() || !isValidSequence(Integer.parseInt(matcher.group(3)))) {
            return false;
        }
        try {
            YearMonth.parse(matcher.group(2), PERIOD_FORMATTER);
        } catch (DateTimeParseException exception) {
            return false;
        }
        String base = reference.substring(0, reference.length() - 4);
        return checksum(base).equals(matcher.group(4));
    }

    private static String normalizePrefix(String prefix) {
        String clean = prefix == null ? "" : prefix.trim().toUpperCase(Locale.ROOT);
        if (!clean.matches("[A-Z0-9]{2,8}")) {
            throw new IllegalArgumentException("payment_reference_prefix_invalid");
        }
        return clean;
    }

    private static String checksum(String base) {
        CRC32 crc = new CRC32();
        crc.update(base.getBytes(StandardCharsets.UTF_8));
        long value = crc.getValue();
        char[] out = new char[4];
        for (int i = 3; i >= 0; i--) {
            out[i] = CHECK_ALPHABET[(int) (value % CHECK_ALPHABET.length)];
            value = value / CHECK_ALPHABET.length;
        }
        return new String(out);
    }
}
