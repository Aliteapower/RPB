package com.rpb.reservation.reservation.application.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class StoreShareDateTimeFormatter {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Singapore");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public String formatDate(Instant instant, String timezone) {
        if (instant == null) {
            return "";
        }
        return DATE_FORMATTER.withZone(zoneId(timezone)).format(instant);
    }

    public String formatTime(Instant instant, String timezone) {
        if (instant == null) {
            return "";
        }
        return TIME_FORMATTER.withZone(zoneId(timezone)).format(instant);
    }

    private static ZoneId zoneId(String timezone) {
        try {
            return hasText(timezone) ? ZoneId.of(timezone.trim()) : DEFAULT_ZONE;
        } catch (RuntimeException exception) {
            return DEFAULT_ZONE;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
