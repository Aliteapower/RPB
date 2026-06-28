package com.rpb.reservation.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.reservation.application.port.out.PlatformReservationShareTemplateSeedRepository;
import com.rpb.reservation.reservation.application.service.PlatformReservationShareTemplateSeedService;
import com.rpb.reservation.reservation.application.service.PlatformReservationShareTemplateSeedServiceErrorCode;
import com.rpb.reservation.reservation.application.service.PlatformReservationShareTemplateSeedServiceException;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateCatalog;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlatformReservationShareTemplateSeedServiceTest {
    private FakePlatformReservationShareTemplateSeedRepository repository;
    private PlatformReservationShareTemplateSeedService service;

    @BeforeEach
    void setUp() {
        repository = new FakePlatformReservationShareTemplateSeedRepository();
        service = new PlatformReservationShareTemplateSeedService(repository);
    }

    @Test
    void readsAndUpdatesDefaultReservationShareTemplateSeed() {
        PlatformReservationShareTemplateSeed current = service.getDefaultSeed();

        assertThat(current.seedKey()).isEqualTo(ReservationShareTemplateCatalog.defaultSeedKey());
        assertThat(current.templateText()).contains("{{guestSalutation}}");
        assertThat(current.allowedVariables()).contains("tableCode", "holdMinutes", "guestSalutation");

        PlatformReservationShareTemplateSeed updated = service.updateDefaultSeed(new PlatformReservationShareTemplateSeedCommand(
            "预约确认模板",
            "zh-CN",
            "尊敬的 {{contactName}} {{guestSalutation}}，桌位：{{tableCode}}，保留 {{holdMinutes}}分钟",
            "active",
            current.version()
        ));

        assertThat(updated.displayName()).isEqualTo("预约确认模板");
        assertThat(updated.templateText()).contains("{{tableCode}}");
        assertThat(updated.version()).isEqualTo(current.version() + 1);
    }

    @Test
    void rejectsUnknownTemplateVariablesBeforeUpdatingSeed() {
        PlatformReservationShareTemplateSeed current = service.getDefaultSeed();

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

        assertThatThrownBy(service::getDefaultSeed)
            .isInstanceOf(PlatformReservationShareTemplateSeedServiceException.class)
            .extracting(exception -> ((PlatformReservationShareTemplateSeedServiceException) exception).code())
            .isEqualTo(PlatformReservationShareTemplateSeedServiceErrorCode.SEED_NOT_FOUND);
    }

    private static final class FakePlatformReservationShareTemplateSeedRepository
        implements PlatformReservationShareTemplateSeedRepository {

        private PlatformReservationShareTemplateSeed seed = new PlatformReservationShareTemplateSeed(
            ReservationShareTemplateCatalog.defaultSeedKey(),
            "餐厅预约确认模板 V1",
            "zh-CN",
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
}
