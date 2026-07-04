package com.rpb.reservation.tenantadmin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.reservation.application.ReservationShareTemplateSeed;
import com.rpb.reservation.reservation.application.port.out.ReservationShareTemplateSeedPort;
import com.rpb.reservation.reservation.application.service.PhoneMaskingPolicy;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateCatalog;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateRenderer;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateSeedService;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.tenantadmin.persistence.TenantAdminShareProfileRepository;
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
                "您好，您的预约信息：{{reservationCode}}，到店时间 {{reservedStartAt}}，人数 {{partySize}}。"
            )
        );

        assertThat(preview.shareText())
            .isEqualTo("您好，您的预约信息：R-PREVIEW-0001，到店时间 20-06-2030 11:30，人数 4。");
    }

    private static final class Scenario {
        private final FakeTenantAdminShareProfileRepository repository = new FakeTenantAdminShareProfileRepository();
        private final TenantAdminShareProfileService service = new TenantAdminShareProfileService(
            repository,
            new ReservationShareTemplateRenderer(),
            new ReservationShareTemplateSeedService(new FakeReservationShareTemplateSeedPort()),
            new PhoneMaskingPolicy()
        );

        private static Scenario ready() {
            return new Scenario();
        }
    }

    private static final class FakeTenantAdminShareProfileRepository extends TenantAdminShareProfileRepository {
        private Row row = new Row(
            "后台门店",
            "食刻订位中心",
            "1 Example Road",
            "https://maps.app.goo.gl/rpb",
            "6333 1234",
            "+6588880000",
            "请提前 10 分钟到店",
            ReservationShareTemplateCatalog.defaultTemplate()
        );

        private FakeTenantAdminShareProfileRepository() {
            super(null);
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
            row = new Row(
                row.storeDisplayName(),
                input.shareDisplayName(),
                row.shareAddress(),
                input.googleMapUrl(),
                row.shareContactPhone(),
                input.whatsappBusinessPhoneE164(),
                input.reservationShareNote(),
                input.reservationShareTemplate()
            );
            return true;
        }

        @Override
        public boolean updateTemplate(StoreScope scope, String reservationShareTemplate) {
            if (!SCOPE.equals(scope)) {
                return false;
            }
            row = new Row(
                row.storeDisplayName(),
                row.shareDisplayName(),
                row.shareAddress(),
                row.googleMapUrl(),
                row.shareContactPhone(),
                row.whatsappBusinessPhoneE164(),
                row.reservationShareNote(),
                reservationShareTemplate
            );
            return true;
        }
    }

    private static final class FakeReservationShareTemplateSeedPort implements ReservationShareTemplateSeedPort {
        @Override
        public Optional<ReservationShareTemplateSeed> findActiveBySeedKey(String seedKey) {
            return Optional.of(new ReservationShareTemplateSeed(seedKey, ReservationShareTemplateCatalog.defaultTemplate()));
        }
    }
}
