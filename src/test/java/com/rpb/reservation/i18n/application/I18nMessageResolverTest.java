package com.rpb.reservation.i18n.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.i18n.application.port.out.I18nRuntimeMessageRepository;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class I18nMessageResolverTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000981");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000981");
    private static final String TITLE_KEY = "call_screen.seed.restaurant_default.slide_2.title";
    private static final String SUBTITLE_KEY = "call_screen.seed.restaurant_default.slide_2.subtitle";
    private static final String TAGLINE_KEY = "call_screen.seed.restaurant_default.slide_2.tagline";

    private FakeRuntimeMessageRepository repository;
    private CatalogI18nMessageResolver resolver;

    @BeforeEach
    void setUp() {
        repository = new FakeRuntimeMessageRepository();
        resolver = new CatalogI18nMessageResolver(repository);
    }

    @Test
    void resolvesStoreTenantPlatformMessagesForRequestedLocaleAndFallsBackToChinese() {
        StoreScope scope = new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));
        repository.add(null, null, TITLE_KEY, "en-SG", "Platform title", "active");
        repository.add(TENANT_ID, null, TITLE_KEY, "en-SG", "Tenant title", "active");
        repository.add(TENANT_ID, STORE_ID, TITLE_KEY, "en-SG", "Store title", "active");
        repository.add(null, null, SUBTITLE_KEY, "zh-CN", "平台中文副标题", "active");
        repository.add(null, null, TAGLINE_KEY, "en-SG", "Inactive tagline", "inactive");

        Map<String, String> resolved = resolver.resolve(scope, List.of(TITLE_KEY, SUBTITLE_KEY, TAGLINE_KEY), "en-SG");

        assertThat(resolved).containsEntry(TITLE_KEY, "Store title");
        assertThat(resolved).containsEntry(SUBTITLE_KEY, "平台中文副标题");
        assertThat(resolved).doesNotContainKey(TAGLINE_KEY);
        assertThat(repository.requestedKeys).containsExactlyInAnyOrder(TITLE_KEY, SUBTITLE_KEY, TAGLINE_KEY);
    }

    @Test
    void unsupportedLocaleUsesChineseFallbackWithoutLosingTenantScope() {
        StoreScope scope = new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));
        repository.add(TENANT_ID, null, TITLE_KEY, "zh-CN", "租户中文标题", "active");
        repository.add(null, null, TITLE_KEY, "zh-CN", "平台中文标题", "active");

        Map<String, String> resolved = resolver.resolve(scope, List.of(TITLE_KEY), "fr-FR");

        assertThat(resolved).containsEntry(TITLE_KEY, "租户中文标题");
    }

    private static final class FakeRuntimeMessageRepository implements I18nRuntimeMessageRepository {
        private final Map<ScopeKey, List<I18nCatalogStoredMessage>> messages = new LinkedHashMap<>();
        private List<String> requestedKeys = List.of();

        private void add(UUID tenantId, UUID storeId, String i18nKey, String locale, String message, String status) {
            messages.computeIfAbsent(new ScopeKey(tenantId, storeId), ignored -> new ArrayList<>())
                .add(new I18nCatalogStoredMessage(UUID.randomUUID(), i18nKey, locale, message, status, 0));
        }

        @Override
        public List<I18nCatalogStoredMessage> findMessages(UUID tenantId, UUID storeId, Collection<String> i18nKeys) {
            requestedKeys = List.copyOf(i18nKeys);
            return messages.getOrDefault(new ScopeKey(tenantId, storeId), List.of()).stream()
                .filter(message -> i18nKeys.contains(message.i18nKey()))
                .toList();
        }
    }

    private record ScopeKey(UUID tenantId, UUID storeId) {
    }
}
