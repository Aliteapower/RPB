package com.rpb.reservation.i18n.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.i18n.application.port.out.I18nCatalogRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class I18nCatalogService {
    private static final List<String> SUPPORTED_LOCALES = List.of("zh-CN", "en-SG");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");

    private final I18nCatalogRepository repository;

    public I18nCatalogService(I18nCatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public I18nCatalogView platformCatalog() {
        return buildView(repository.findActiveKeys(), null);
    }

    @Transactional
    public I18nCatalogView updatePlatformCatalog(List<I18nCatalogMessageCommand> commands) {
        Map<String, I18nCatalogKey> allowedKeys = keysByName(repository.findActiveKeys());
        for (I18nCatalogMessageCommand command : normalizeCommands(commands)) {
            I18nCatalogKey key = requireAllowedKey(allowedKeys, command.i18nKey());
            validateCommand(key, command, false);
            boolean updated = repository.upsertMessage(
                null,
                null,
                command.i18nKey().trim(),
                command.locale().trim(),
                command.message().trim(),
                normalizeStatus(command.status()),
                command.version()
            );
            if (!updated) {
                throw new I18nCatalogServiceException(I18nCatalogServiceErrorCode.VERSION_CONFLICT);
            }
        }
        return platformCatalog();
    }

    @Transactional(readOnly = true)
    public I18nCatalogView tenantCatalog(StoreScope scope) {
        return buildView(repository.findTenantEditableActiveKeys(), scope);
    }

    @Transactional
    public I18nCatalogView updateTenantCatalog(
        StoreScope scope,
        String scopeLevel,
        List<I18nCatalogMessageCommand> commands
    ) {
        String normalizedScopeLevel = normalizeScopeLevel(scopeLevel);
        Map<String, I18nCatalogKey> allowedKeys = keysByName(repository.findTenantEditableActiveKeys());
        UUID tenantId = scope.tenantId().value();
        UUID storeId = "store".equals(normalizedScopeLevel) ? scope.storeId().value() : null;
        for (I18nCatalogMessageCommand command : normalizeCommands(commands)) {
            I18nCatalogKey key = requireAllowedKey(allowedKeys, command.i18nKey());
            validateCommand(key, command, true);
            boolean updated = command.clear()
                ? repository.clearMessage(tenantId, storeId, command.i18nKey().trim(), command.locale().trim(), command.version())
                : repository.upsertMessage(
                    tenantId,
                    storeId,
                    command.i18nKey().trim(),
                    command.locale().trim(),
                    command.message().trim(),
                    normalizeStatus(command.status()),
                    command.version()
                );
            if (!updated) {
                throw new I18nCatalogServiceException(I18nCatalogServiceErrorCode.VERSION_CONFLICT);
            }
        }
        return tenantCatalog(scope);
    }

    private I18nCatalogView buildView(List<I18nCatalogKey> keys, StoreScope scope) {
        Map<String, Map<String, I18nCatalogStoredMessage>> platformMessages = messagesByKeyAndLocale(repository.findMessages(null, null));
        Map<String, Map<String, I18nCatalogStoredMessage>> tenantMessages = scope == null
            ? Map.of()
            : messagesByKeyAndLocale(repository.findMessages(scope.tenantId().value(), null));
        Map<String, Map<String, I18nCatalogStoredMessage>> storeMessages = scope == null
            ? Map.of()
            : messagesByKeyAndLocale(repository.findMessages(scope.tenantId().value(), scope.storeId().value()));

        List<I18nCatalogEntry> entries = keys.stream()
            .map(key -> new I18nCatalogEntry(key, localeViews(key, platformMessages, tenantMessages, storeMessages)))
            .toList();
        return new I18nCatalogView(SUPPORTED_LOCALES, entries);
    }

    private List<I18nCatalogLocaleView> localeViews(
        I18nCatalogKey key,
        Map<String, Map<String, I18nCatalogStoredMessage>> platformMessages,
        Map<String, Map<String, I18nCatalogStoredMessage>> tenantMessages,
        Map<String, Map<String, I18nCatalogStoredMessage>> storeMessages
    ) {
        List<I18nCatalogLocaleView> views = new ArrayList<>();
        for (String locale : SUPPORTED_LOCALES) {
            I18nCatalogStoredMessage platform = message(platformMessages, key.i18nKey(), locale);
            I18nCatalogStoredMessage tenant = message(tenantMessages, key.i18nKey(), locale);
            I18nCatalogStoredMessage store = message(storeMessages, key.i18nKey(), locale);
            I18nCatalogStoredMessage effective = firstActive(store, tenant, platform);
            views.add(new I18nCatalogLocaleView(
                locale,
                platform,
                tenant,
                store,
                effective == null ? "" : effective.message(),
                effectiveSource(effective, store, tenant, platform)
            ));
        }
        return views;
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

    private static String effectiveSource(
        I18nCatalogStoredMessage effective,
        I18nCatalogStoredMessage store,
        I18nCatalogStoredMessage tenant,
        I18nCatalogStoredMessage platform
    ) {
        if (effective == null) {
            return "frontend_fallback";
        }
        if (effective == store) {
            return "store";
        }
        if (effective == tenant) {
            return "tenant";
        }
        if (effective == platform) {
            return "platform";
        }
        return "frontend_fallback";
    }

    private static Map<String, Map<String, I18nCatalogStoredMessage>> messagesByKeyAndLocale(
        List<I18nCatalogStoredMessage> messages
    ) {
        Map<String, Map<String, I18nCatalogStoredMessage>> byKey = new LinkedHashMap<>();
        for (I18nCatalogStoredMessage message : messages) {
            byKey.computeIfAbsent(message.i18nKey(), ignored -> new HashMap<>())
                .put(message.locale(), message);
        }
        return byKey;
    }

    private static Map<String, I18nCatalogKey> keysByName(List<I18nCatalogKey> keys) {
        Map<String, I18nCatalogKey> byName = new LinkedHashMap<>();
        for (I18nCatalogKey key : keys) {
            byName.put(key.i18nKey(), key);
        }
        return byName;
    }

    private static List<I18nCatalogMessageCommand> normalizeCommands(List<I18nCatalogMessageCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new I18nCatalogServiceException(I18nCatalogServiceErrorCode.REQUEST_INVALID);
        }
        return commands.stream()
            .map(command -> {
                if (command == null) {
                    throw new I18nCatalogServiceException(I18nCatalogServiceErrorCode.REQUEST_INVALID);
                }
                return command;
            })
            .toList();
    }

    private static I18nCatalogKey requireAllowedKey(Map<String, I18nCatalogKey> allowedKeys, String i18nKey) {
        String normalized = clean(i18nKey);
        I18nCatalogKey key = allowedKeys.get(normalized);
        if (key == null) {
            throw new I18nCatalogServiceException(I18nCatalogServiceErrorCode.KEY_NOT_ALLOWED);
        }
        return key;
    }

    private static void validateCommand(I18nCatalogKey key, I18nCatalogMessageCommand command, boolean allowClear) {
        if (command.clear()) {
            if (!allowClear) {
                throw new I18nCatalogServiceException(I18nCatalogServiceErrorCode.REQUEST_INVALID);
            }
            requireSupportedLocale(command.locale());
            return;
        }
        requireSupportedLocale(command.locale());
        normalizeStatus(command.status());
        String message = clean(command.message());
        if (message.isBlank()) {
            throw new I18nCatalogServiceException(I18nCatalogServiceErrorCode.REQUEST_INVALID);
        }
        if (!unknownPlaceholders(message, key.placeholderNames()).isEmpty()) {
            throw new I18nCatalogServiceException(I18nCatalogServiceErrorCode.PLACEHOLDER_UNKNOWN);
        }
    }

    private static void requireSupportedLocale(String locale) {
        if (!SUPPORTED_LOCALES.contains(clean(locale))) {
            throw new I18nCatalogServiceException(I18nCatalogServiceErrorCode.LOCALE_NOT_SUPPORTED);
        }
    }

    private static String normalizeStatus(String status) {
        String normalized = clean(status);
        if (!"active".equals(normalized) && !"inactive".equals(normalized)) {
            throw new I18nCatalogServiceException(I18nCatalogServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static String normalizeScopeLevel(String scopeLevel) {
        String normalized = clean(scopeLevel);
        if (!"tenant".equals(normalized) && !"store".equals(normalized)) {
            throw new I18nCatalogServiceException(I18nCatalogServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static List<String> unknownPlaceholders(String message, List<String> allowedNames) {
        Set<String> allowed = Set.copyOf(allowedNames);
        LinkedHashSet<String> unknown = new LinkedHashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(message);
        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            if (!allowed.contains(variableName)) {
                unknown.add(variableName);
            }
        }
        return List.copyOf(unknown);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
