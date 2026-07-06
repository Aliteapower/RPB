package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.i18n.application.I18nCatalogStoredMessage;
import com.rpb.reservation.i18n.application.port.out.I18nCatalogRepository;
import com.rpb.reservation.i18n.application.port.out.I18nRuntimeMessageRepository;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateSeedService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
final class TenantAdminShareProfileTextCatalog {
    static final String ARRIVAL_NOTE_KEY = "reservation.share.arrival_note";
    static final String TEMPLATE_KEY = "reservation.share.restaurant_reservation_confirmation_v1";

    private static final String FALLBACK_LOCALE = "zh-CN";
    private static final List<String> SUPPORTED_LOCALES = List.of(FALLBACK_LOCALE, "en-SG");
    private static final List<String> SHARE_TEXT_KEYS = List.of(ARRIVAL_NOTE_KEY, TEMPLATE_KEY);

    private final I18nRuntimeMessageRepository runtimeMessageRepository;
    private final I18nCatalogRepository catalogRepository;
    private final ReservationShareTemplateSeedService templateSeedService;

    TenantAdminShareProfileTextCatalog(
        I18nRuntimeMessageRepository runtimeMessageRepository,
        I18nCatalogRepository catalogRepository,
        ReservationShareTemplateSeedService templateSeedService
    ) {
        this.runtimeMessageRepository = runtimeMessageRepository;
        this.catalogRepository = catalogRepository;
        this.templateSeedService = templateSeedService;
    }

    ResolvedShareText resolve(
        StoreScope scope,
        String locale,
        String legacyArrivalNote,
        String legacyTemplate
    ) {
        String normalizedLocale = normalizeLocale(locale);
        ScopeMessages platform = messages(null, null, SHARE_TEXT_KEYS);
        ScopeMessages tenant = messages(scope.tenantId().value(), null, SHARE_TEXT_KEYS);
        ScopeMessages store = messages(scope.tenantId().value(), scope.storeId().value(), SHARE_TEXT_KEYS);

        ResolvedMessage arrivalNote = resolveMessage(platform, tenant, store, ARRIVAL_NOTE_KEY, normalizedLocale, true);
        ResolvedMessage template = resolveMessage(platform, tenant, store, TEMPLATE_KEY, normalizedLocale, true);
        ResolvedMessage defaultTemplate = resolveMessage(platform, tenant, store, TEMPLATE_KEY, normalizedLocale, false);
        String defaultTemplateText = firstText(message(defaultTemplate), templateSeedService.defaultTemplate());

        return new ResolvedShareText(
            normalizedLocale,
            firstText(message(arrivalNote), legacyArrivalNote),
            firstText(message(template), legacyTemplate, defaultTemplateText),
            defaultTemplateText,
            usesDefaultTemplate(template, legacyTemplate, defaultTemplateText)
        );
    }

    void saveStoreOverride(StoreScope scope, String locale, String i18nKey, String message) {
        String normalizedLocale = normalizeLocale(locale);
        boolean updated = hasText(message)
            ? catalogRepository.upsertMessage(
                scope.tenantId().value(),
                scope.storeId().value(),
                i18nKey,
                normalizedLocale,
                message.trim(),
                "active",
                null
            )
            : catalogRepository.clearMessage(
                scope.tenantId().value(),
                scope.storeId().value(),
                i18nKey,
                normalizedLocale,
                null
            );
        if (!updated) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.PERSISTENCE_ERROR);
        }
    }

    void clearStoreOverride(StoreScope scope, String locale, String i18nKey) {
        boolean updated = catalogRepository.clearMessage(
            scope.tenantId().value(),
            scope.storeId().value(),
            i18nKey,
            normalizeLocale(locale),
            null
        );
        if (!updated) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.PERSISTENCE_ERROR);
        }
    }

    String normalizeLocale(String locale) {
        String normalized = clean(locale);
        return SUPPORTED_LOCALES.contains(normalized) ? normalized : FALLBACK_LOCALE;
    }

    boolean isFallbackLocale(String locale) {
        return FALLBACK_LOCALE.equals(normalizeLocale(locale));
    }

    private ScopeMessages messages(UUID tenantId, UUID storeId, Collection<String> i18nKeys) {
        return new ScopeMessages(runtimeMessageRepository.findMessages(tenantId, storeId, i18nKeys));
    }

    private ResolvedMessage resolveMessage(
        ScopeMessages platform,
        ScopeMessages tenant,
        ScopeMessages store,
        String i18nKey,
        String locale,
        boolean includeStore
    ) {
        ResolvedMessage requested = firstActive(
            includeStore ? new ResolvedMessage("store", store.message(i18nKey, locale)) : null,
            new ResolvedMessage("tenant", tenant.message(i18nKey, locale)),
            new ResolvedMessage("platform", platform.message(i18nKey, locale))
        );
        if (requested != null || FALLBACK_LOCALE.equals(locale)) {
            return requested;
        }
        return firstActive(
            includeStore ? new ResolvedMessage("store", store.message(i18nKey, FALLBACK_LOCALE)) : null,
            new ResolvedMessage("tenant", tenant.message(i18nKey, FALLBACK_LOCALE)),
            new ResolvedMessage("platform", platform.message(i18nKey, FALLBACK_LOCALE))
        );
    }

    private static ResolvedMessage firstActive(ResolvedMessage... messages) {
        for (ResolvedMessage message : messages) {
            if (message != null && hasText(message.message())) {
                return message;
            }
        }
        return null;
    }

    private static boolean usesDefaultTemplate(ResolvedMessage template, String legacyTemplate, String defaultTemplate) {
        if (template != null) {
            return !"store".equals(template.source());
        }
        return !hasText(legacyTemplate) || legacyTemplate.trim().equals(defaultTemplate);
    }

    private static String message(ResolvedMessage message) {
        return message == null ? null : message.message();
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

    private record ResolvedMessage(String source, String message) {
    }

    record ResolvedShareText(
        String locale,
        String arrivalNote,
        String template,
        String defaultTemplate,
        boolean usesDefaultTemplate
    ) {
    }

    private record ScopeMessages(Map<String, Map<String, I18nCatalogStoredMessage>> messagesByKeyAndLocale) {
        ScopeMessages(List<I18nCatalogStoredMessage> messages) {
            this(messagesByKeyAndLocale(messages));
        }

        String message(String i18nKey, String locale) {
            I18nCatalogStoredMessage message = messagesByKeyAndLocale
                .getOrDefault(i18nKey, Map.of())
                .get(locale);
            return message == null ? null : message.message();
        }

        private static Map<String, Map<String, I18nCatalogStoredMessage>> messagesByKeyAndLocale(
            List<I18nCatalogStoredMessage> messages
        ) {
            Map<String, Map<String, I18nCatalogStoredMessage>> byKey = new LinkedHashMap<>();
            for (I18nCatalogStoredMessage message : messages) {
                if (message.active() && hasText(message.message())) {
                    byKey.computeIfAbsent(message.i18nKey(), ignored -> new LinkedHashMap<>())
                        .put(message.locale(), message);
                }
            }
            return byKey;
        }
    }
}
