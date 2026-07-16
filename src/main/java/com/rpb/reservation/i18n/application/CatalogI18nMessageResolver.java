package com.rpb.reservation.i18n.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.i18n.application.port.out.I18nRuntimeMessageRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CatalogI18nMessageResolver implements I18nMessageResolver {
    private static final String FALLBACK_LOCALE = "zh-CN";
    private static final List<String> SUPPORTED_LOCALES = List.of(FALLBACK_LOCALE, "en-SG");

    private final I18nRuntimeMessageRepository repository;

    public CatalogI18nMessageResolver(I18nRuntimeMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<String, String> resolve(StoreScope scope, Collection<String> i18nKeys, String locale) {
        List<String> keys = normalizeKeys(i18nKeys);
        if (keys.isEmpty()) {
            return Map.of();
        }

        String requestedLocale = normalizeLocale(locale);
        Map<String, Map<String, I18nCatalogStoredMessage>> platformMessages = messagesByKeyAndLocale(
            repository.findMessages(null, null, keys)
        );
        Map<String, Map<String, I18nCatalogStoredMessage>> tenantMessages = scope == null
            ? Map.of()
            : messagesByKeyAndLocale(repository.findMessages(scope.tenantId().value(), null, keys));
        Map<String, Map<String, I18nCatalogStoredMessage>> storeMessages = scope == null
            ? Map.of()
            : messagesByKeyAndLocale(repository.findMessages(scope.tenantId().value(), scope.storeId().value(), keys));

        Map<String, String> resolved = new LinkedHashMap<>();
        for (String key : keys) {
            I18nCatalogStoredMessage requested = firstActive(
                message(storeMessages, key, requestedLocale),
                message(tenantMessages, key, requestedLocale),
                message(platformMessages, key, requestedLocale)
            );
            I18nCatalogStoredMessage fallback = requestedLocale.equals(FALLBACK_LOCALE)
                ? null
                : firstActive(
                    message(storeMessages, key, FALLBACK_LOCALE),
                    message(tenantMessages, key, FALLBACK_LOCALE),
                    message(platformMessages, key, FALLBACK_LOCALE)
                );
            I18nCatalogStoredMessage effective = requested == null ? fallback : requested;
            if (effective != null) {
                resolved.put(key, effective.message());
            }
        }
        return resolved;
    }

    private static List<String> normalizeKeys(Collection<String> i18nKeys) {
        if (i18nKeys == null || i18nKeys.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String key : i18nKeys) {
            String normalized = clean(key);
            if (!normalized.isBlank()) {
                keys.add(normalized);
            }
        }
        return List.copyOf(keys);
    }

    private static String normalizeLocale(String locale) {
        String normalized = clean(locale);
        return SUPPORTED_LOCALES.contains(normalized) ? normalized : FALLBACK_LOCALE;
    }

    private static Map<String, Map<String, I18nCatalogStoredMessage>> messagesByKeyAndLocale(
        List<I18nCatalogStoredMessage> messages
    ) {
        Map<String, Map<String, I18nCatalogStoredMessage>> byKey = new LinkedHashMap<>();
        for (I18nCatalogStoredMessage message : messages) {
            if (message.active() && !clean(message.message()).isBlank()) {
                byKey.computeIfAbsent(message.i18nKey(), ignored -> new LinkedHashMap<>())
                    .put(message.locale(), message);
            }
        }
        return byKey;
    }

    private static I18nCatalogStoredMessage message(
        Map<String, Map<String, I18nCatalogStoredMessage>> source,
        String i18nKey,
        String locale
    ) {
        return source.getOrDefault(i18nKey, Map.of()).get(locale);
    }

    private static I18nCatalogStoredMessage firstActive(I18nCatalogStoredMessage... messages) {
        for (I18nCatalogStoredMessage message : messages) {
            if (message != null && message.active()) {
                return message;
            }
        }
        return null;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
