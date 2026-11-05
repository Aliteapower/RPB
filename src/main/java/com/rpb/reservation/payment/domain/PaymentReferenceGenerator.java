package com.rpb.reservation.payment.domain;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.CRC32;

public final class PaymentReferenceGenerator {
    private static final int MAX_SEQUENCE = 9999;
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd")
        .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("uuuuMM")
        .withResolverStyle(ResolverStyle.STRICT);
    private static final char[] CHECK_ALPHABET = "ACDEFGHJKMNPQRTVWXY".toCharArray();

    private PaymentReferenceGenerator() {
    }

    public static String generate(String prefix, LocalDate businessDate, int sequence) {
        String cleanPrefix = normalizePrefix(prefix);
        if (businessDate == null || !isValidSequence(sequence)) {
            throw new IllegalArgumentException("payment_reference_sequence_invalid");
        }
        String sequenceText = "%04d".formatted(sequence);
        String base = cleanPrefix + businessDate.format(DAY_FORMATTER) + sequenceText;
        return base + checksum(base);
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
        return compactReferenceParts(value).isPresent();
    }

    static Optional<CompactReferenceParts> compactReferenceParts(String value) {
        String reference = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!reference.matches("[A-Z0-9]{2,8}\\d{10}(?:\\d{2})?[ACDEFGHJKMNPQRTVWXY]{4}")) {
            return Optional.empty();
        }
        return compactReferenceParts(reference, 8)
            .or(() -> compactReferenceParts(reference, 6));
    }

    private static Optional<CompactReferenceParts> compactReferenceParts(String reference, int dateLength) {
        int checkStart = reference.length() - 4;
        int sequenceStart = checkStart - 4;
        int dateStart = sequenceStart - dateLength;
        if (dateStart < 2 || dateStart > 8) {
            return Optional.empty();
        }
        String prefix = reference.substring(0, dateStart);
        String dateSegment = reference.substring(dateStart, sequenceStart);
        String sequenceText = reference.substring(sequenceStart, checkStart);
        String checkSegment = reference.substring(checkStart);
        if (!prefix.matches("[A-Z0-9]{2,8}") || !isValidSequence(Integer.parseInt(sequenceText))) {
            return Optional.empty();
        }
        if (!isValidDateSegment(dateSegment)) {
            return Optional.empty();
        }
        String base = reference.substring(0, checkStart);
        return checksum(base).equals(checkSegment)
            ? Optional.of(new CompactReferenceParts(prefix, dateSegment, sequenceText, checkSegment))
            : Optional.empty();
    }

    private static boolean isValidDateSegment(String dateSegment) {
        try {
            if (dateSegment.length() == 8) {
                LocalDate.parse(dateSegment, DAY_FORMATTER);
                return true;
            }
            if (dateSegment.length() == 6) {
                YearMonth.parse(dateSegment, PERIOD_FORMATTER);
                return true;
            }
        } catch (DateTimeParseException exception) {
            return false;
        }
        return false;
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

    record CompactReferenceParts(String prefix, String dateSegment, String sequenceText, String checkSegment) {
    }
}
