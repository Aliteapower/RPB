package com.rpb.reservation.reservation.application.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class ReservationShareTemplateCatalog {
    private static final String DEFAULT_SEED_KEY = "restaurant_reservation_confirmation_v1";

    private static final List<String> ALLOWED_VARIABLES = List.of(
        "storeName",
        "reservationNo",
        "reservationDate",
        "reservationTime",
        "partySize",
        "tableCode",
        "holdMinutes",
        "contactName",
        "guestSalutation",
        "maskedPhone",
        "storeAddress",
        "googleMapUrl",
        "storePhone",
        "arrivalNote",
        "confirmInstruction",
        "cancelInstruction",
        "changeInstruction",
        "replyInstruction"
    );

    private static final List<String> LEGACY_ALIAS_VARIABLES = List.of(
        "reservationCode",
        "reservedStartAt"
    );

    private static final List<String> SUPPORTED_VARIABLES = supportedVariableList();

    private static final String DEFAULT_TEMPLATE = """
        Dear {{contactName}} {{guestSalutation}},

        Thank you for choosing {{storeName}}. We are pleased to confirm your booking details below:

        Booking no.: {{reservationNo}}

        Date: {{reservationDate}}

        Time: {{reservationTime}}

        Party size: {{partySize}} pax

        Table: {{tableCode}} (reserved)

        Hold time: To protect every guest's dining experience, we will hold your table for {{holdMinutes}} minutes. If you arrive after the hold time, the table may be released.

        Arrival note: {{arrivalNote}}

        Store address: {{storeAddress}}

        Contact phone: {{storePhone}}

        To change or cancel, please contact the store at least 2 hours ahead.

        We look forward to serving you.

        Best regards,
        {{storeName}} Reservations
        """;

    private ReservationShareTemplateCatalog() {
    }

    public static List<String> allowedVariables() {
        return ALLOWED_VARIABLES;
    }

    public static List<String> supportedVariables() {
        return SUPPORTED_VARIABLES;
    }

    public static void applyLegacyAliases(Map<String, String> variables) {
        if (variables == null) {
            return;
        }
        variables.put("reservationCode", clean(variables.get("reservationNo")));
        variables.put("reservedStartAt", joinDateTime(
            variables.get("reservationDate"),
            variables.get("reservationTime")
        ));
    }

    public static String defaultSeedKey() {
        return DEFAULT_SEED_KEY;
    }

    public static String defaultTemplate() {
        return DEFAULT_TEMPLATE.stripTrailing();
    }

    private static String joinDateTime(String date, String time) {
        return (clean(date) + " " + clean(time)).trim();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> supportedVariableList() {
        LinkedHashSet<String> variables = new LinkedHashSet<>();
        variables.addAll(ALLOWED_VARIABLES);
        variables.addAll(LEGACY_ALIAS_VARIABLES);
        return List.copyOf(variables);
    }
}
