package com.rpb.reservation.i18n.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.i18n.application.port.out.I18nCatalogRepository;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class I18nCatalogServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000983");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000983");
    private static final StoreScope STORE_SCOPE = new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));

    @Test
    void tenantCatalogResolvesStoreThenTenantThenPlatformFallback() {
        FakeI18nCatalogRepository repository = new FakeI18nCatalogRepository();
        repository.keys.add(key("public_booking.prompt.policy", true, List.of("holdMinutes")));
        repository.messages.add(message(null, null, "public_booking.prompt.policy", "zh-CN", "平台默认", 1));
        repository.messages.add(message(null, null, "public_booking.prompt.policy", "en-SG", "Platform default", 1));
        repository.messages.add(message(TENANT_ID, null, "public_booking.prompt.policy", "zh-CN", "租户覆盖", 2));
        repository.messages.add(message(TENANT_ID, STORE_ID, "public_booking.prompt.policy", "en-SG", "Store override", 3));

        I18nCatalogView view = new I18nCatalogService(repository).tenantCatalog(STORE_SCOPE);

        I18nCatalogEntry entry = view.entries().get(0);
        assertThat(entry.locales()).extracting(I18nCatalogLocaleView::locale)
            .containsExactly("zh-CN", "en-SG");
        assertThat(entry.locales().get(0).effectiveMessage()).isEqualTo("租户覆盖");
        assertThat(entry.locales().get(0).effectiveSource()).isEqualTo("tenant");
        assertThat(entry.locales().get(1).effectiveMessage()).isEqualTo("Store override");
        assertThat(entry.locales().get(1).effectiveSource()).isEqualTo("store");
    }

    @Test
    void tenantUpdateRejectsKeysThatAreNotTenantEditable() {
        FakeI18nCatalogRepository repository = new FakeI18nCatalogRepository();
        repository.keys.add(key("status.reservation.confirmed", false, List.of()));

        I18nCatalogService service = new I18nCatalogService(repository);

        assertThatThrownBy(() -> service.updateTenantCatalog(
            STORE_SCOPE,
            "store",
            List.of(new I18nCatalogMessageCommand("status.reservation.confirmed", "zh-CN", "已预约", "active", null, false))
        ))
            .isInstanceOf(I18nCatalogServiceException.class)
            .extracting("code")
            .isEqualTo(I18nCatalogServiceErrorCode.KEY_NOT_ALLOWED);
    }

    @Test
    void updateRejectsUnknownTemplatePlaceholder() {
        FakeI18nCatalogRepository repository = new FakeI18nCatalogRepository();
        repository.keys.add(key("queue.ticket.customer_notice", true, List.of("queueNo")));

        I18nCatalogService service = new I18nCatalogService(repository);

        assertThatThrownBy(() -> service.updatePlatformCatalog(List.of(
            new I18nCatalogMessageCommand("queue.ticket.customer_notice", "zh-CN", "请 {{badName}} 入座", "active", null, false)
        )))
            .isInstanceOf(I18nCatalogServiceException.class)
            .extracting("code")
            .isEqualTo(I18nCatalogServiceErrorCode.PLACEHOLDER_UNKNOWN);
    }

    @Test
    void platformUpdateWritesOnlyPlatformScopeMessage() {
        FakeI18nCatalogRepository repository = new FakeI18nCatalogRepository();
        repository.keys.add(key("call_screen.welcome.title", true, List.of()));

        I18nCatalogService service = new I18nCatalogService(repository);
        service.updatePlatformCatalog(List.of(
            new I18nCatalogMessageCommand("call_screen.welcome.title", "en-SG", "Welcome back", "active", null, false)
        ));

        assertThat(repository.messages)
            .anySatisfy(message -> {
                assertThat(message.tenantId).isNull();
                assertThat(message.storeId).isNull();
                assertThat(message.i18nKey).isEqualTo("call_screen.welcome.title");
                assertThat(message.locale).isEqualTo("en-SG");
                assertThat(message.message).isEqualTo("Welcome back");
            });
    }

    private static I18nCatalogKey key(String i18nKey, boolean tenantEditable, List<String> placeholders) {
        return new I18nCatalogKey(
            i18nKey,
            i18nKey.substring(0, i18nKey.indexOf('.')),
            "test",
            i18nKey,
            "test",
            "template",
            tenantEditable,
            placeholders,
            "active",
            10
        );
    }

    private static MessageRow message(UUID tenantId, UUID storeId, String i18nKey, String locale, String message, int version) {
        return new MessageRow(UUID.randomUUID(), tenantId, storeId, i18nKey, locale, message, "active", version);
    }

    private static final class FakeI18nCatalogRepository implements I18nCatalogRepository {
        private final List<I18nCatalogKey> keys = new ArrayList<>();
        private final List<MessageRow> messages = new ArrayList<>();

        @Override
        public List<I18nCatalogKey> findActiveKeys() {
            return keys.stream()
                .sorted(Comparator.comparingInt(I18nCatalogKey::sortOrder))
                .toList();
        }

        @Override
        public List<I18nCatalogKey> findTenantEditableActiveKeys() {
            return keys.stream()
                .filter(I18nCatalogKey::tenantEditable)
                .sorted(Comparator.comparingInt(I18nCatalogKey::sortOrder))
                .toList();
        }

        @Override
        public List<I18nCatalogStoredMessage> findMessages(UUID tenantId, UUID storeId) {
            return messages.stream()
                .filter(row -> same(row.tenantId, tenantId) && same(row.storeId, storeId))
                .map(MessageRow::storedMessage)
                .toList();
        }

        @Override
        public boolean upsertMessage(
            UUID tenantId,
            UUID storeId,
            String i18nKey,
            String locale,
            String message,
            String status,
            Integer expectedVersion
        ) {
            MessageRow existing = findRow(tenantId, storeId, i18nKey, locale);
            if (existing != null) {
                if (expectedVersion != null && expectedVersion != existing.version) {
                    return false;
                }
                messages.remove(existing);
                messages.add(new MessageRow(existing.id, tenantId, storeId, i18nKey, locale, message, status, existing.version + 1));
                return true;
            }
            messages.add(new MessageRow(UUID.randomUUID(), tenantId, storeId, i18nKey, locale, message, status, 0));
            return true;
        }

        @Override
        public boolean clearMessage(UUID tenantId, UUID storeId, String i18nKey, String locale, Integer expectedVersion) {
            MessageRow existing = findRow(tenantId, storeId, i18nKey, locale);
            if (existing == null) {
                return true;
            }
            if (expectedVersion != null && expectedVersion != existing.version) {
                return false;
            }
            messages.remove(existing);
            return true;
        }

        private MessageRow findRow(UUID tenantId, UUID storeId, String i18nKey, String locale) {
            return messages.stream()
                .filter(row -> same(row.tenantId, tenantId)
                    && same(row.storeId, storeId)
                    && row.i18nKey.equals(i18nKey)
                    && row.locale.equals(locale))
                .findFirst()
                .orElse(null);
        }

        private static boolean same(UUID left, UUID right) {
            return left == null ? right == null : left.equals(right);
        }
    }

    private record MessageRow(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String i18nKey,
        String locale,
        String message,
        String status,
        int version
    ) {
        I18nCatalogStoredMessage storedMessage() {
            return new I18nCatalogStoredMessage(id, i18nKey, locale, message, status, version);
        }
    }
}
