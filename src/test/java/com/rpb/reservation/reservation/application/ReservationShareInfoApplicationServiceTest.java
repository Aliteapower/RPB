package com.rpb.reservation.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.reservation.application.port.out.ReservationShareInfoReadPort;
import com.rpb.reservation.reservation.application.port.out.ReservationShareInfoRow;
import com.rpb.reservation.reservation.application.port.out.ReservationShareTemplateSeedPort;
import com.rpb.reservation.reservation.application.port.out.ReservationPublicShareTokenPort;
import com.rpb.reservation.reservation.application.query.ReservationShareInfoQuery;
import com.rpb.reservation.reservation.application.service.PhoneMaskingPolicy;
import com.rpb.reservation.reservation.application.service.ReservationShareInfoApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateCatalog;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateRenderer;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateSeedService;
import com.rpb.reservation.reservation.application.service.StoreShareDateTimeFormatter;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationShareInfoApplicationServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000001101");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000001101");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000001101");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000001101");

    @Test
    void rendersCustomShareInfoUsingStoreProfileReservationCustomerAndMaskedPhone() {
        Scenario scenario = Scenario.ready();
        scenario.readPort.row = row("""
            门店：{{storeName}}
            编号：{{reservationNo}}
            时间：{{reservationDate}} {{reservationTime}}
            人数：{{partySize}}
            桌位：{{tableCode}}
            保留：{{holdMinutes}} 分钟
            联系人：{{contactName}}
            电话：{{maskedPhone}}
            地址：{{storeAddress}}
            地图：{{googleMapUrl}}
            提示：{{arrivalNote}}
            门店电话：{{storePhone}}
            """);

        ReservationShareInfoResult result = scenario.service.getShareInfo(query());

        assertThat(result.success()).isTrue();
        ReservationShareInfo shareInfo = result.shareInfo();
        assertThat(shareInfo.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(shareInfo.reservationNo()).isEqualTo("R-20300620-0007");
        assertThat(shareInfo.channel()).isEqualTo("manual_copy");
        assertThat(shareInfo.customerMaskedPhone()).isEqualTo("****4567");
        assertThat(shareInfo.customerPhoneAvailable()).isTrue();
        assertThat(shareInfo.senderLabel()).isEqualTo("+6588880000");
        assertThat(shareInfo.canOpenWhatsAppLink()).isTrue();
        assertThat(shareInfo.whatsappLink())
            .startsWith("https://wa.me/6591234567?text=")
            .contains("https%3A%2F%2Fstaff.rpb.test%2Freservation-share%2Fshare-token-1")
            .doesNotContain("+6591234567");
        assertThat(shareInfo.canOpenWechatLink()).isTrue();
        assertThat(shareInfo.wechatLink()).isEqualTo("weixin://");
        assertThat(shareInfo.wechatShareText())
            .contains("门店：食刻订位中心")
            .contains("https://staff.rpb.test/reservation-share/share-token-1")
            .doesNotContain("+6591234567");
        assertThat(shareInfo.shareToken()).isEqualTo("share-token-1");
        assertThat(shareInfo.sharePath()).isEqualTo("/reservation-share/share-token-1");
        assertThat(shareInfo.shareTitle()).isEqualTo("食刻订位中心 订位确认");
        assertThat(shareInfo.shareSummary()).isEqualTo("20-06-2030 11:30 · 4人");
        assertThat(shareInfo.shareText())
            .contains("门店：食刻订位中心")
            .contains("编号：R-20300620-0007")
            .contains("时间：20-06-2030 11:30")
            .contains("人数：4")
            .contains("桌位：A01")
            .contains("保留：15 分钟")
            .contains("联系人：Ada Guest")
            .contains("电话：****4567")
            .contains("地址：1 Example Road")
            .contains("地图：https://maps.app.goo.gl/rpb")
            .contains("提示：请提前 10 分钟到店")
            .contains("门店电话：6333 1234");
    }

    @Test
    void fallsBackToDefaultTemplateWhenStoredTemplateContainsUnknownVariable() {
        Scenario scenario = Scenario.ready();
        scenario.readPort.row = row("门店：{{storeName}}\n未知：{{unsupportedVariable}}");

        ReservationShareInfoResult result = scenario.service.getShareInfo(query());

        assertThat(result.success()).isTrue();
        assertThat(result.shareInfo().shareText())
            .contains("尊敬的 Ada Guest 先生/女士")
            .contains("感谢您选择 食刻订位中心")
            .contains("预订编号：R-20300620-0007")
            .contains("日期：20-06-2030")
            .contains("时间：11:30")
            .contains("人数：4位成人")
            .contains("桌位：A01 (已预留)")
            .contains("预留时间：为保证所有宾客的用餐体验，我们将为您保留座位 15分钟")
            .contains("到店提示：请提前 10 分钟到店")
            .contains("门店地址：1 Example Road")
            .contains("联系电话：6333 1234")
            .contains("如需修改或取消，请至少提前 2 小时联系门店。")
            .contains("食刻订位中心 预订部")
            .doesNotContain("https://maps.app.goo.gl/rpb")
            .doesNotContain("unsupportedVariable");
    }

    @Test
    void rendersLegacyReservationCodeAndReservedStartAtTemplateAliases() {
        Scenario scenario = Scenario.ready();
        scenario.readPort.row = row("您好，您的预约信息：{{reservationCode}}，到店时间 {{reservedStartAt}}，人数 {{partySize}}。");

        ReservationShareInfoResult result = scenario.service.getShareInfo(query());

        assertThat(result.success()).isTrue();
        assertThat(result.shareInfo().shareText())
            .isEqualTo("您好，您的预约信息：R-20300620-0007，到店时间 20-06-2030 11:30，人数 4。");
    }

    @Test
    void emptyCustomerPhoneReturnsEmptyMaskedPhoneAndUnavailableFlag() {
        Scenario scenario = Scenario.ready();
        scenario.readPort.row = rowWithoutPhone();

        ReservationShareInfoResult result = scenario.service.getShareInfo(query());

        assertThat(result.success()).isTrue();
        assertThat(result.shareInfo().customerMaskedPhone()).isEmpty();
        assertThat(result.shareInfo().customerPhoneAvailable()).isFalse();
        assertThat(result.shareInfo().canOpenWhatsAppLink()).isFalse();
        assertThat(result.shareInfo().whatsappLink()).isNull();
        assertThat(result.shareInfo().canOpenWechatLink()).isTrue();
        assertThat(result.shareInfo().sharePath()).isEqualTo("/reservation-share/share-token-1");
        assertThat(result.shareInfo().shareText()).doesNotContain("null");
    }

    @Test
    void reusesExistingActivePublicShareTokenForReservation() {
        Scenario scenario = Scenario.ready();
        scenario.publicShareTokenPort.token = "existing-token";

        ReservationShareInfoResult result = scenario.service.getShareInfo(query());

        assertThat(result.success()).isTrue();
        assertThat(result.shareInfo().shareToken()).isEqualTo("existing-token");
        assertThat(result.shareInfo().sharePath()).isEqualTo("/reservation-share/existing-token");
        assertThat(scenario.publicShareTokenPort.ensureCalls).isEqualTo(1);
    }

    @Test
    void returnsReservationNotFoundInsideRequestedTenantStoreScope() {
        Scenario scenario = Scenario.ready();
        scenario.readPort.row = null;

        ReservationShareInfoResult result = scenario.service.getShareInfo(query());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationShareInfoError.RESERVATION_NOT_FOUND);
    }

    private static ReservationShareInfoQuery query() {
        return new ReservationShareInfoQuery(
            TENANT_ID,
            STORE_ID,
            RESERVATION_ID,
            ACTOR_ID,
            "staff",
            "https://staff.rpb.test"
        );
    }

    private static ReservationShareInfoRow row(String template) {
        return new ReservationShareInfoRow(
            RESERVATION_ID,
            "R-20300620-0007",
            4,
            Instant.parse("2030-06-20T03:30:00Z"),
            Instant.parse("2030-06-20T03:45:00Z"),
            "A01",
            "Ada Guest",
            "VIP",
            "+6591234567",
            "Reservation Integration Store",
            "Asia/Singapore",
            "食刻订位中心",
            "1 Example Road",
            "https://maps.app.goo.gl/rpb",
            "6333 1234",
            "+6588880000",
            "请提前 10 分钟到店",
            template
        );
    }

    private static ReservationShareInfoRow rowWithoutPhone() {
        return new ReservationShareInfoRow(
            RESERVATION_ID,
            "R-20300620-0007",
            2,
            Instant.parse("2030-06-20T03:30:00Z"),
            Instant.parse("2030-06-20T03:45:00Z"),
            null,
            "No Phone Guest",
            null,
            null,
            "Reservation Integration Store",
            "Asia/Singapore",
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private static final class Scenario {
        private final StoreScope scope = new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));
        private final FakeStoreRepository storeRepository = new FakeStoreRepository(scope);
        private final FakeReservationShareInfoReadPort readPort = new FakeReservationShareInfoReadPort();
        private final FakeReservationPublicShareTokenPort publicShareTokenPort = new FakeReservationPublicShareTokenPort();
        private final ReservationShareInfoApplicationService service = new ReservationShareInfoApplicationService(
            storeRepository,
            readPort,
            publicShareTokenPort,
            new ReservationShareTemplateRenderer(),
            new ReservationShareTemplateSeedService(new FakeReservationShareTemplateSeedPort()),
            new PhoneMaskingPolicy(),
            new StoreShareDateTimeFormatter(),
            () -> "generated-token"
        );

        private static Scenario ready() {
            Scenario scenario = new Scenario();
            scenario.readPort.row = row(null);
            return scenario;
        }
    }

    private static final class FakeStoreRepository implements StoreRepositoryPort {
        private final StoreScope scope;
        private final Store store;

        private FakeStoreRepository(StoreScope scope) {
            this.scope = scope;
            this.store = new Store(scope.storeId(), scope.tenantId(), "STORE-1", "Asia/Singapore", "en-SG", "active");
        }

        @Override
        public Optional<Store> findById(StoreScope requestedScope) {
            return scope.equals(requestedScope) ? Optional.of(store) : Optional.empty();
        }

        @Override
        public Optional<StorePolicy> findCurrentPolicy(StoreScope scope, OffsetDateTime at) {
            return Optional.empty();
        }

        @Override
        public Store save(StoreScope scope, Store store) {
            return store;
        }

        @Override
        public StorePolicy savePolicy(StoreScope scope, StorePolicy policy) {
            return policy;
        }
    }

    private static final class FakeReservationShareInfoReadPort implements ReservationShareInfoReadPort {
        private ReservationShareInfoRow row;

        @Override
        public Optional<ReservationShareInfoRow> findByReservationId(StoreScope scope, UUID reservationId) {
            if (!scope.storeId().value().equals(STORE_ID) || !reservationId.equals(RESERVATION_ID)) {
                return Optional.empty();
            }
            return Optional.ofNullable(row);
        }
    }

    private static final class FakeReservationPublicShareTokenPort implements ReservationPublicShareTokenPort {
        private String token = "share-token-1";
        private int ensureCalls;

        @Override
        public String ensureActiveToken(StoreScope scope, UUID reservationId, String tokenCandidate) {
            ensureCalls++;
            assertThat(scope.tenantId().value()).isEqualTo(TENANT_ID);
            assertThat(scope.storeId().value()).isEqualTo(STORE_ID);
            assertThat(reservationId).isEqualTo(RESERVATION_ID);
            assertThat(tokenCandidate).isEqualTo("generated-token");
            return token;
        }
    }

    private static final class FakeReservationShareTemplateSeedPort implements ReservationShareTemplateSeedPort {
        @Override
        public Optional<ReservationShareTemplateSeed> findActiveBySeedKey(String seedKey) {
            return Optional.of(new ReservationShareTemplateSeed(seedKey, ReservationShareTemplateCatalog.defaultTemplate()));
        }
    }
}
