package com.rpb.reservation.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.i18n.application.I18nCatalogKey;
import com.rpb.reservation.i18n.application.I18nCatalogStoredMessage;
import com.rpb.reservation.i18n.application.port.out.I18nCatalogRepository;
import com.rpb.reservation.i18n.application.port.out.I18nRuntimeMessageRepository;
import com.rpb.reservation.reservation.application.port.out.PlatformReservationShareTemplateSeedRepository;
import com.rpb.reservation.reservation.application.service.PlatformReservationShareTemplateSeedService;
import com.rpb.reservation.reservation.application.service.PlatformReservationShareTemplateSeedServiceErrorCode;
import com.rpb.reservation.reservation.application.service.PlatformReservationShareTemplateSeedServiceException;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateCatalog;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateRenderer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlatformReservationShareTemplateSeedServiceTest {
    private static final String TEMPLATE_KEY = "reservation.share.restaurant_reservation_confirmation_v1";
    private static final String CHINESE_TEMPLATE = """
        尊敬的 {{contactName}}{{guestSalutation}}：

        感谢选择 {{storeName}}，您的订位信息如下：

        订位号：{{reservationNo}}

        日期：{{reservationDate}}
        """.stripTrailing();

    private FakePlatformReservationShareTemplateSeedRepository repository;
    private FakeI18nRepository i18nRepository;
    private PlatformReservationShareTemplateSeedService service;

    @BeforeEach
    void setUp() {
        repository = new FakePlatformReservationShareTemplateSeedRepository();
        i18nRepository = new FakeI18nRepository();
        i18nRepository.put("zh-CN", CHINESE_TEMPLATE, "active", 4);
        i18nRepository.put("en-SG", ReservationShareTemplateCatalog.defaultTemplate(), "active", 7);
        service = new PlatformReservationShareTemplateSeedService(
            repository,
            new ReservationShareTemplateRenderer(),
            i18nRepository,
            i18nRepository
        );
    }

    @Test
    void readsAndUpdatesDefaultReservationShareTemplateSeed() {
        PlatformReservationShareTemplateSeed current = service.getDefaultSeed("en-SG");

        assertThat(current.seedKey()).isEqualTo(ReservationShareTemplateCatalog.defaultSeedKey());
        assertThat(current.templateText()).contains("{{guestSalutation}}");
        assertThat(current.allowedVariables()).contains("tableCode", "holdMinutes", "guestSalutation");

        PlatformReservationShareTemplateSeed updated = service.updateDefaultSeed(new PlatformReservationShareTemplateSeedCommand(
            "Reservation confirmation template",
            "en-SG",
            "Dear {{contactName}} {{guestSalutation}}, table {{tableCode}} is held for {{holdMinutes}} minutes.",
            "active",
            current.version()
        ));

        assertThat(updated.displayName()).isEqualTo("Reservation confirmation template");
        assertThat(updated.templateText()).contains("{{tableCode}}");
        assertThat(updated.version()).isEqualTo(current.version() + 1);
        assertThat(i18nRepository.message("en-SG").message()).contains("table {{tableCode}}");
    }

    @Test
    void readsDefaultReservationShareTemplateSeedByRequestedLocale() {
        PlatformReservationShareTemplateSeed zhSeed = service.getDefaultSeed("zh-CN");
        PlatformReservationShareTemplateSeed enSeed = service.getDefaultSeed("en-SG");

        assertThat(zhSeed.locale()).isEqualTo("zh-CN");
        assertThat(zhSeed.templateText()).contains("感谢选择 {{storeName}}");
        assertThat(zhSeed.version()).isEqualTo(4);

        assertThat(enSeed.locale()).isEqualTo("en-SG");
        assertThat(enSeed.templateText()).contains("Thank you for choosing {{storeName}}");
        assertThat(enSeed.version()).isEqualTo(7);
    }

    @Test
    void rejectsUnknownTemplateVariablesBeforeUpdatingSeed() {
        PlatformReservationShareTemplateSeed current = service.getDefaultSeed("zh-CN");

        assertThatThrownBy(() -> service.updateDefaultSeed(new PlatformReservationShareTemplateSeedCommand(
            "预约确认模板",
            "zh-CN",
            "编号：{{reservationNo}} {{unsupportedVariable}}",
            "active",
            current.version()
        )))
            .isInstanceOf(PlatformReservationShareTemplateSeedServiceException.class)
            .extracting(exception -> ((PlatformReservationShareTemplateSeedServiceException) exception).code())
            .isEqualTo(PlatformReservationShareTemplateSeedServiceErrorCode.TEMPLATE_UNKNOWN_VARIABLE);
    }

    @Test
    void rejectsStaleVersionAndInvalidStatus() {
        assertThatThrownBy(() -> service.updateDefaultSeed(new PlatformReservationShareTemplateSeedCommand(
            "预约确认模板",
            "zh-CN",
            "编号：{{reservationNo}}",
            "active",
            99
        )))
            .isInstanceOf(PlatformReservationShareTemplateSeedServiceException.class)
            .extracting(exception -> ((PlatformReservationShareTemplateSeedServiceException) exception).code())
            .isEqualTo(PlatformReservationShareTemplateSeedServiceErrorCode.VERSION_CONFLICT);

        assertThatThrownBy(() -> service.updateDefaultSeed(new PlatformReservationShareTemplateSeedCommand(
            "预约确认模板",
            "zh-CN",
            "编号：{{reservationNo}}",
            "archived",
            repository.seed.version()
        )))
            .isInstanceOf(PlatformReservationShareTemplateSeedServiceException.class)
            .extracting(exception -> ((PlatformReservationShareTemplateSeedServiceException) exception).code())
            .isEqualTo(PlatformReservationShareTemplateSeedServiceErrorCode.REQUEST_INVALID);
    }

    @Test
    void reportsMissingSeedAsStableError() {
        repository.seed = null;
        i18nRepository.clear();

        assertThatThrownBy(service::getDefaultSeed)
            .isInstanceOf(PlatformReservationShareTemplateSeedServiceException.class)
            .extracting(exception -> ((PlatformReservationShareTemplateSeedServiceException) exception).code())
            .isEqualTo(PlatformReservationShareTemplateSeedServiceErrorCode.SEED_NOT_FOUND);
    }

    private static final class FakePlatformReservationShareTemplateSeedRepository
        implements PlatformReservationShareTemplateSeedRepository {

        private PlatformReservationShareTemplateSeed seed = new PlatformReservationShareTemplateSeed(
            ReservationShareTemplateCatalog.defaultSeedKey(),
            "Restaurant reservation confirmation template V1",
            "en-SG",
            ReservationShareTemplateCatalog.defaultTemplate(),
            "active",
            0,
            ReservationShareTemplateCatalog.allowedVariables()
        );

        @Override
        public Optional<PlatformReservationShareTemplateSeed> findBySeedKey(String seedKey) {
            return Optional.ofNullable(seed).filter(value -> value.seedKey().equals(seedKey));
        }

        @Override
        public PlatformReservationShareTemplateSeed update(
            String seedKey,
            String displayName,
            String locale,
            String templateText,
            String status
        ) {
            seed = new PlatformReservationShareTemplateSeed(
                seedKey,
                displayName,
                locale,
                templateText,
                status,
                seed.version() + 1,
                ReservationShareTemplateCatalog.allowedVariables()
            );
            return seed;
        }
    }

    private static final class FakeI18nRepository implements I18nRuntimeMessageRepository, I18nCatalogRepository {
        private final Map<String, I18nCatalogStoredMessage> messagesByLocale = new LinkedHashMap<>();

        void put(String locale, String message, String status, int version) {
            messagesByLocale.put(locale, stored(locale, message, status, version));
        }

        I18nCatalogStoredMessage message(String locale) {
            return messagesByLocale.get(locale);
        }

        void clear() {
            messagesByLocale.clear();
        }

        @Override
        public List<I18nCatalogStoredMessage> findMessages(UUID tenantId, UUID storeId, Collection<String> i18nKeys) {
            if (tenantId != null || storeId != null || i18nKeys == null || !i18nKeys.contains(TEMPLATE_KEY)) {
                return List.of();
            }
            return List.copyOf(messagesByLocale.values());
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
            if (tenantId != null || storeId != null || !TEMPLATE_KEY.equals(i18nKey)) {
                return false;
            }
            I18nCatalogStoredMessage current = messagesByLocale.get(locale);
            if (current != null && expectedVersion != null && current.version() != expectedVersion) {
                return false;
            }
            if (current == null && expectedVersion != null && expectedVersion > 0) {
                return false;
            }
            messagesByLocale.put(locale, stored(locale, message, status, current == null ? 0 : current.version() + 1));
            return true;
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
            return findMessages(tenantId, storeId, List.of(TEMPLATE_KEY));
        }

        @Override
        public boolean clearMessage(UUID tenantId, UUID storeId, String i18nKey, String locale, Integer expectedVersion) {
            return false;
        }

        private static I18nCatalogStoredMessage stored(String locale, String message, String status, int version) {
            return new I18nCatalogStoredMessage(UUID.randomUUID(), TEMPLATE_KEY, locale, message, status, version);
        }
    }
}
