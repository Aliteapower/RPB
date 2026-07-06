package com.rpb.reservation.reservation.application.service;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.i18n.application.I18nMessageResolver;
import java.util.List;
import java.util.Map;

final class ReservationShareRuntimeTextResolver {
    static final String ARRIVAL_NOTE_KEY = "reservation.share.arrival_note";
    static final String TEMPLATE_KEY = "reservation.share.restaurant_reservation_confirmation_v1";

    private final ReservationShareTemplateSeedService templateSeedService;
    private final I18nMessageResolver i18nMessageResolver;

    ReservationShareRuntimeTextResolver(
        ReservationShareTemplateSeedService templateSeedService,
        I18nMessageResolver i18nMessageResolver
    ) {
        this.templateSeedService = templateSeedService;
        this.i18nMessageResolver = i18nMessageResolver;
    }

    ReservationShareRuntimeText resolve(
        StoreScope scope,
        String locale,
        String legacyArrivalNote,
        String legacyTemplate
    ) {
        Map<String, String> messages = i18nMessageResolver.resolve(
            scope,
            List.of(ARRIVAL_NOTE_KEY, TEMPLATE_KEY),
            locale
        );
        String defaultTemplate = templateSeedService.defaultTemplate();
        return new ReservationShareRuntimeText(
            normalizeLocale(locale),
            firstText(messages.get(ARRIVAL_NOTE_KEY), legacyArrivalNote),
            firstText(messages.get(TEMPLATE_KEY), legacyTemplate, defaultTemplate),
            defaultTemplate
        );
    }

    private static String normalizeLocale(String locale) {
        return hasText(locale) ? locale.trim() : "zh-CN";
    }

    private static String firstText(String first, String second) {
        if (hasText(first)) {
            return first.trim();
        }
        return clean(second);
    }

    private static String firstText(String first, String second, String third) {
        if (hasText(first)) {
            return first.trim();
        }
        if (hasText(second)) {
            return second.trim();
        }
        return clean(third);
    }

    private static String clean(String value) {
        return hasText(value) ? value.trim() : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record ReservationShareRuntimeText(
        String locale,
        String arrivalNote,
        String template,
        String defaultTemplate
    ) {
        String tablePendingLabel() {
            return english() ? "To be confirmed" : "待确认";
        }

        String guestSalutation() {
            return english() ? "" : "先生/女士";
        }

        String shareTitle(String storeName) {
            String name = hasText(storeName) ? storeName.trim() : (english() ? "Store" : "门店");
            return english() ? name + " booking confirmation" : name + " 订位确认";
        }

        String shareSummary(String date, String time, String partySize) {
            String dateTime = (clean(date) + " " + clean(time)).trim();
            String party = hasText(partySize) ? partySize.trim() + (english() ? " pax" : "人") : "";
            if (!dateTime.isBlank() && !party.isBlank()) {
                return dateTime + " · " + party;
            }
            return dateTime.isBlank() ? party : dateTime;
        }

        private boolean english() {
            return "en-SG".equalsIgnoreCase(locale);
        }
    }
}
