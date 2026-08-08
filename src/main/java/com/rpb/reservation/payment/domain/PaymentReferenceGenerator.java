package com.rpb.reservation.payment.domain;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.zip.CRC32;

public final class PaymentReferenceGenerator {
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private PaymentReferenceGenerator() {
    }

    public static String generate(String prefix, YearMonth period, int sequence) {
        String cleanPrefix = normalizePrefix(prefix);
        if (period == null || sequence <= 0 || sequence > 999999) {
            throw new IllegalArgumentException("payment_reference_sequence_invalid");
        }
        String base = cleanPrefix + "-" + period.format(PERIOD_FORMATTER) + "-" + "%04d".formatted(sequence);
        return base + "-" + checksum(base);
    }

    private static String normalizePrefix(String prefix) {
        String clean = prefix == null ? "" : prefix.trim().toUpperCase();
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
            out[i] = ALPHABET[(int) (value % ALPHABET.length)];
            value = value / ALPHABET.length;
        }
        return new String(out);
    }
}
