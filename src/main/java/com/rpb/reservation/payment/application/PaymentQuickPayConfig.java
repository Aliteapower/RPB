package com.rpb.reservation.payment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record PaymentQuickPayConfig(
    String referencePrefix,
    int dailyStartNumber,
    List<BigDecimal> presetAmounts
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_REFERENCE_PREFIX = "QP";
    private static final int DEFAULT_DAILY_START_NUMBER = 0;
    private static final List<BigDecimal> DEFAULT_PRESET_AMOUNTS = List.of(
        new BigDecimal("5"),
        new BigDecimal("10"),
        new BigDecimal("20"),
        new BigDecimal("50"),
        new BigDecimal("100"),
        new BigDecimal("200")
    );

    public static PaymentQuickPayConfig defaults() {
        return new PaymentQuickPayConfig(DEFAULT_REFERENCE_PREFIX, DEFAULT_DAILY_START_NUMBER, DEFAULT_PRESET_AMOUNTS);
    }

    public static PaymentQuickPayConfig fromJson(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return defaults();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(configJson);
            JsonNode quickPay = root.path("quickPay");
            if (quickPay.isMissingNode() || quickPay.isNull()) {
                quickPay = root;
            }
            return new PaymentQuickPayConfig(
                normalizeReferencePrefix(quickPay.path("referencePrefix").asText(DEFAULT_REFERENCE_PREFIX)),
                normalizeDailyStartNumber(quickPay.path("dailyStartNumber").asInt(DEFAULT_DAILY_START_NUMBER)),
                normalizePresetAmounts(quickPay.path("presetAmounts"))
            );
        } catch (Exception exception) {
            return defaults();
        }
    }

    public static String normalizeReferencePrefix(String value) {
        String cleaned = String.valueOf(value == null ? "" : value)
            .replaceAll("[^A-Za-z0-9]", "")
            .toUpperCase(Locale.ROOT);
        if (cleaned.isBlank()) {
            return DEFAULT_REFERENCE_PREFIX;
        }
        return cleaned.length() <= 3 ? cleaned : cleaned.substring(0, 3);
    }

    private static int normalizeDailyStartNumber(int value) {
        return Math.max(0, Math.min(value, 9999));
    }

    private static List<BigDecimal> normalizePresetAmounts(JsonNode node) {
        if (!node.isArray()) {
            return DEFAULT_PRESET_AMOUNTS;
        }
        List<BigDecimal> amounts = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isNumber() && !item.isTextual()) {
                continue;
            }
            try {
                BigDecimal amount = new BigDecimal(item.asText()).stripTrailingZeros();
                if (amount.signum() > 0 && !amounts.contains(amount)) {
                    amounts.add(amount);
                }
            } catch (NumberFormatException ignored) {
                // Invalid entries are ignored so one bad preset does not disable quick pay.
            }
            if (amounts.size() >= 6) {
                break;
            }
        }
        return amounts.isEmpty() ? DEFAULT_PRESET_AMOUNTS : List.copyOf(amounts);
    }
}
