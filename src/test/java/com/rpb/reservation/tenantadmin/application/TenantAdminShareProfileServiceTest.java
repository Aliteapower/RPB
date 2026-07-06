package com.rpb.reservation.tenantadmin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.i18n.application.I18nCatalogKey;
import com.rpb.reservation.i18n.application.I18nCatalogStoredMessage;
import com.rpb.reservation.i18n.application.port.out.I18nCatalogRepository;
import com.rpb.reservation.i18n.application.port.out.I18nRuntimeMessageRepository;
import com.rpb.reservation.reservation.application.ReservationShareTemplateSeed;
import com.rpb.reservation.reservation.application.port.out.ReservationShareTemplateSeedPort;
import com.rpb.reservation.reservation.application.service.PhoneMaskingPolicy;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateCatalog;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateRenderer;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateSeedService;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenantadmin.persistence.TenantAdminShareProfileRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantAdminShareProfileServiceTest {
    private static final StoreScope SCOPE = new StoreScope(
        new TenantId(UUID.fromString("10000000-0000-0000-0000-000000001601")),
        new StoreId(UUID.fromString("20000000-0000-0000-0000-000000001601"))
    );

    @Test
    void previewsLegacyReservationCodeAndReservedStartAtTemplateAliases() {
        Scenario scenario = Scenario.ready();

        TenantAdminSharePreview preview = scenario.service.preview(
            SCOPE,
            new TenantAdminShareProfileCommand(
                null,
                null,
                null,
                null,
                null,
                "您好，您的预约信息：{{reservationCode}}，到店时间 {{reservedStartAt}}，人数 {{partySize}}。"
            )
        );

        assertThat(preview.shareText())
            .isEqualTo("您好，您的预约信息：R-PREVIEW-0001，到店时间 20-06-2030 11:30，人数 4。");
    }

    @Test
    void exposesLegacyTemplateAliasesAsAvailableVariables() {
        Scenario scenario = Scenario.ready();

        TenantAdminShareProfile profile = scenario.service.getProfile(SCOPE);

        assertThat(profile.availableVariables())
            .contains("reservationNo", "reservationCode", "reservedStartAt");
    }

    @Test
    void resolvesRequestedLocaleShareTextFromI18nCatalogBeforeLegacyStoreColumns() {
        Scenario scenario = Scenario.ready();
        scenario.i18n.upsertPlatform(
            TenantAdminShareProfileTextCatalog.ARRIVAL_NOTE_KEY,
            "en-SG",
            "Please arrive 10 minutes early."
        );
        scenario.i18n.upsertPlatform(
            TenantAdminShareProfileTextCatalog.TEMPLATE_KEY,
            "en-SG",
            "Dear {{contactName}}, your booking {{reservationNo}} is confirmed."
        );
        scenario.repository.useLegacyShareText("中文旧提示", "中文旧模板 {{reservationNo}}");

        TenantAdminShareProfile profile = scenario.service.getProfile(SCOPE, "en-SG");

        assertThat(profile.reservationShareNote()).isEqualTo("Please arrive 10 minutes early.");
        assertThat(profile.reservationShareTemplate()).isEqualTo("Dear {{contactName}}, your booking {{reservationNo}} is confirmed.");
        assertThat(profile.defaultReservationShareTemplate()).isEqualTo("Dear {{contactName}}, your booking {{reservationNo}} is confirmed.");
        assertThat(profile.usesDefaultReservationShareTemplate()).isTrue();
        assertThat(scenario.repository.updateTemplateCalls).isZero();
    }

    @Test
    void updatesEnglishShareTextAsStoreI18nOverrideWithoutOverwritingLegacyChineseColumns() {
        Scenario scenario = Scenario.ready();
        scenario.i18n.upsertPlatform(
            TenantAdminShareProfileTextCatalog.TEMPLATE_KEY,
            "en-SG",
            "Platform English template {{reservationNo}}"
        );
        scenario.repository.useLegacyShareText("中文旧提示", "中文旧模板 {{reservationNo}}");

        TenantAdminShareProfile profile = scenario.service.updateProfile(
            SCOPE,
            new TenantAdminShareProfileCommand(
                "English Booking Desk",
                "https://maps.example/en",
                "booking-en@example.test",
                "+6588880001",
                "Please arrive on time.",
                "English override {{reservationNo}} {{storeName}}"
            ),
            "en-SG"
        );

        assertThat(profile.reservationShareNote()).isEqualTo("Please arrive on time.");
        assertThat(profile.reservationShareTemplate()).isEqualTo("English override {{reservationNo}} {{storeName}}");
        assertThat(profile.usesDefaultReservationShareTemplate()).isFalse();
        assertThat(scenario.i18n.message(SCOPE, TenantAdminShareProfileTextCatalog.ARRIVAL_NOTE_KEY, "en-SG"))
            .isEqualTo("Please arrive on time.");
        assertThat(scenario.i18n.message(SCOPE, TenantAdminShareProfileTextCatalog.TEMPLATE_KEY, "en-SG"))
            .isEqualTo("English override {{reservationNo}} {{storeName}}");
        assertThat(scenario.repository.row.reservationShareNote()).isEqualTo("中文旧提示");
        assertThat(scenario.repository.row.reservationShareTemplate()).isEqualTo("中文旧模板 {{reservationNo}}");
        assertThat(scenario.repository.row.shareDisplayName()).isEqualTo("English Booking Desk");
        assertThat(scenario.repository.updateContactSettingsCalls).isEqualTo(1);
        assertThat(scenario.repository.updateCalls).isZero();
    }

    @Test
    void updatesChineseShareTextInI18nCatalogAndLegacyColumnsForCompatibility() {
        Scenario scenario = Scenario.ready();

        scenario.service.updateProfile(
            SCOPE,
            new TenantAdminShareProfileCommand(
                "食刻订位中心",
                "https://maps.example/zh",
                "booking@example.test",
                "+6588880000",
                "请提前到店",
                "中文覆盖 {{reservationNo}}"
            ),
            "zh-CN"
        );

        assertThat(scenario.i18n.message(SCOPE, TenantAdminShareProfileTextCatalog.ARRIVAL_NOTE_KEY, "zh-CN"))
            .isEqualTo("请提前到店");
        assertThat(scenario.i18n.message(SCOPE, TenantAdminShareProfileTextCatalog.TEMPLATE_KEY, "zh-CN"))
            .isEqualTo("中文覆盖 {{reservationNo}}");
        assertThat(scenario.repository.row.reservationShareNote()).isEqualTo("请提前到店");
        assertThat(scenario.repository.row.reservationShareTemplate()).isEqualTo("中文覆盖 {{reservationNo}}");
        assertThat(scenario.repository.updateCalls).isEqualTo(1);
    }

    @Test
    void restoringEnglishDefaultTemplateClearsOnlyEnglishStoreOverride() {
        Scenario scenario = Scenario.ready();
        scenario.i18n.upsertPlatform(
            TenantAdminShareProfileTextCatalog.TEMPLATE_KEY,
            "en-SG",
            "Platform English template {{reservationNo}}"
        );
        scenario.i18n.upsertStore(
            SCOPE,
            TenantAdminShareProfileTextCatalog.TEMPLATE_KEY,
            "en-SG",
            "Store English template {{reservationNo}}"
        );
        scenario.repository.useLegacyShareText("中文旧提示", "中文旧模板 {{reservationNo}}");

        TenantAdminShareProfile profile = scenario.service.restoreDefaultTemplate(SCOPE, "en-SG");

        assertThat(profile.reservationShareTemplate()).isEqualTo("Platform English template {{reservationNo}}");
        assertThat(profile.usesDefaultReservationShareTemplate()).isTrue();
        assertThat(scenario.i18n.message(SCOPE, TenantAdminShareProfileTextCatalog.TEMPLATE_KEY, "en-SG")).isNull();
        assertThat(scenario.repository.row.reservationShareTemplate()).isEqualTo("中文旧模板 {{reservationNo}}");
        assertThat(scenario.repository.updateTemplateCalls).isZero();
    }

    private static final class Scenario {
        private final FakeTenantAdminShareProfileRepository repository = new FakeTenantAdminShareProfileRepository();
        private final FakeI18nCatalogRepository i18n = new FakeI18nCatalogRepository();
        private final TenantAdminShareProfileService service = new TenantAdminShareProfileService(
            repository,
            new ReservationShareTemplateRenderer(),
            new TenantAdminShareProfileTextCatalog(
                i18n,
                i18n,
                new ReservationShareTemplateSeedService(new FakeReservationShareTemplateSeedPort())
            ),
            new PhoneMaskingPolicy()
        );

        private static Scenario ready() {
            return new Scenario();
        }
    }

    private static final class FakeTenantAdminShareProfileRepository extends TenantAdminShareProfileRepository {
        private Row row = row(
            "后台门店",
            "食刻订位中心",
            "1 Example Road",
            "https://maps.app.goo.gl/rpb",
            "6333 1234",
            "booking@example.test",
            "+6588880000",
            "请提前 10 分钟到店",
            ReservationShareTemplateCatalog.defaultTemplate()
        );
        private int updateCalls;
        private int updateContactSettingsCalls;
        private int updateTemplateCalls;

        private FakeTenantAdminShareProfileRepository() {
            super(null);
        }

        void useLegacyShareText(String reservationShareNote, String reservationShareTemplate) {
            row = row(
                row.storeDisplayName(),
                row.shareDisplayName(),
                row.shareAddress(),
                row.googleMapUrl(),
                row.shareContactPhone(),
                row.shareEmail(),
                row.whatsappBusinessPhoneE164(),
                reservationShareNote,
                reservationShareTemplate
            );
        }

        @Override
        public Optional<Row> find(StoreScope scope) {
            return SCOPE.equals(scope) ? Optional.of(row) : Optional.empty();
        }

        @Override
        public boolean update(StoreScope scope, TenantAdminShareProfileUpdate input) {
            if (!SCOPE.equals(scope)) {
                return false;
            }
            updateCalls++;
            row = row(
                row.storeDisplayName(),
                input.shareDisplayName(),
                row.shareAddress(),
                input.googleMapUrl(),
                row.shareContactPhone(),
                input.shareEmail(),
                input.whatsappBusinessPhoneE164(),
                input.reservationShareNote(),
                input.reservationShareTemplate()
            );
            return true;
        }

        @Override
        public boolean updateContactSettings(StoreScope scope, TenantAdminShareProfileUpdate input) {
            if (!SCOPE.equals(scope)) {
                return false;
            }
            updateContactSettingsCalls++;
            row = row(
                row.storeDisplayName(),
                input.shareDisplayName(),
                row.shareAddress(),
                input.googleMapUrl(),
                row.shareContactPhone(),
                input.shareEmail(),
                input.whatsappBusinessPhoneE164(),
                row.reservationShareNote(),
                row.reservationShareTemplate()
            );
            return true;
        }

        @Override
        public boolean updateTemplate(StoreScope scope, String reservationShareTemplate) {
            if (!SCOPE.equals(scope)) {
                return false;
            }
            updateTemplateCalls++;
            row = row(
                row.storeDisplayName(),
                row.shareDisplayName(),
                row.shareAddress(),
                row.googleMapUrl(),
                row.shareContactPhone(),
                row.shareEmail(),
                row.whatsappBusinessPhoneE164(),
                row.reservationShareNote(),
                reservationShareTemplate
            );
            return true;
        }

        private static Row row(
            String storeDisplayName,
            String shareDisplayName,
            String shareAddress,
            String googleMapUrl,
            String shareContactPhone,
            String shareEmail,
            String whatsappBusinessPhoneE164,
            String reservationShareNote,
            String reservationShareTemplate
        ) {
            return new Row(
                storeDisplayName,
                shareDisplayName,
                shareAddress,
                googleMapUrl,
                shareContactPhone,
                shareEmail,
                whatsappBusinessPhoneE164,
                reservationShareNote,
                reservationShareTemplate
            );
        }
    }

    private static final class FakeI18nCatalogRepository implements I18nCatalogRepository, I18nRuntimeMessageRepository {
        private final Map<Key, String> messages = new LinkedHashMap<>();

        void upsertPlatform(String i18nKey, String locale, String message) {
            messages.put(Key.platform(i18nKey, locale), message);
        }

        void upsertStore(StoreScope scope, String i18nKey, String locale, String message) {
            messages.put(Key.store(scope, i18nKey, locale), message);
        }

        String message(StoreScope scope, String i18nKey, String locale) {
            return messages.get(Key.store(scope, i18nKey, locale));
        }

        @Override
        public List<I18nCatalogKey> findActiveKeys() {
            return List.of();
        }

        @Override
        public List<I18nCatalogKey> findTenantEditableActiveKeys() {
            return List.of();
        }

        @Override
        public List<I18nCatalogStoredMessage> findMessages(UUID tenantId, UUID storeId) {
            return findMessages(tenantId, storeId, messages.keySet().stream().map(Key::i18nKey).toList());
        }

        @Override
        public List<I18nCatalogStoredMessage> findMessages(UUID tenantId, UUID storeId, Collection<String> i18nKeys) {
            return messages.entrySet().stream()
                .filter(entry -> entry.getKey().matches(tenantId, storeId))
                .filter(entry -> i18nKeys.contains(entry.getKey().i18nKey()))
                .map(entry -> entry.getKey().toStoredMessage(entry.getValue()))
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
            messages.put(new Key(tenantId, storeId, i18nKey, locale), message);
            return true;
        }

        @Override
        public boolean clearMessage(UUID tenantId, UUID storeId, String i18nKey, String locale, Integer expectedVersion) {
            messages.remove(new Key(tenantId, storeId, i18nKey, locale));
            return true;
        }
    }

    private record Key(UUID tenantId, UUID storeId, String i18nKey, String locale) {
        static Key platform(String i18nKey, String locale) {
            return new Key(null, null, i18nKey, locale);
        }

        static Key store(StoreScope scope, String i18nKey, String locale) {
            return new Key(scope.tenantId().value(), scope.storeId().value(), i18nKey, locale);
        }

        boolean matches(UUID otherTenantId, UUID otherStoreId) {
            return java.util.Objects.equals(tenantId, otherTenantId)
                && java.util.Objects.equals(storeId, otherStoreId);
        }

        I18nCatalogStoredMessage toStoredMessage(String message) {
            return new I18nCatalogStoredMessage(
                UUID.nameUUIDFromBytes((String.valueOf(tenantId) + storeId + i18nKey + locale).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                i18nKey,
                locale,
                message,
                "active",
                0
            );
        }
    }

    private static final class FakeReservationShareTemplateSeedPort implements ReservationShareTemplateSeedPort {
        @Override
        public Optional<ReservationShareTemplateSeed> findActiveBySeedKey(String seedKey) {
            return Optional.of(new ReservationShareTemplateSeed(seedKey, ReservationShareTemplateCatalog.defaultTemplate()));
        }
    }
}
