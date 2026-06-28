package com.rpb.reservation.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.reservation.application.port.out.ReservationPublicShareReadPort;
import com.rpb.reservation.reservation.application.port.out.ReservationPublicShareRow;
import com.rpb.reservation.reservation.application.service.ReservationPublicShareApplicationService;
import com.rpb.reservation.reservation.application.service.StoreShareDateTimeFormatter;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationPublicShareApplicationServiceTest {
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000001301");
    private static final Instant NOW = Instant.parse("2030-06-18T00:00:00Z");

    @Test
    void returnsCustomerSafePublicShareViewForActiveToken() {
        Scenario scenario = Scenario.ready();

        ReservationPublicShareResult result = scenario.service.getPublicShare("share-token-1");

        assertThat(result.success()).isTrue();
        ReservationPublicShare share = result.share();
        assertThat(share.reservationNo()).isEqualTo("R-PUBLIC-0007");
        assertThat(share.storeName()).isEqualTo("食刻订位中心");
        assertThat(share.reservationDate()).isEqualTo("20-06-2030");
        assertThat(share.reservationTime()).isEqualTo("11:30");
        assertThat(share.partySize()).isEqualTo(4);
        assertThat(share.tableCode()).isEqualTo("A01");
        assertThat(share.tablePending()).isFalse();
        assertThat(share.arrivalNote()).isEqualTo("请提前 10 分钟到店");
        assertThat(share.storePhone()).isEqualTo("6333 1234");
        assertThat(share.storeAddress()).isEqualTo("1 Example Road");
        assertThat(share.googleMapUrl()).isEqualTo("https://maps.app.goo.gl/rpb");
        assertThat(share.shareTitle()).isEqualTo("食刻订位中心 订位确认");
        assertThat(share.shareSummary()).isEqualTo("20-06-2030 11:30 · 4人");
    }

    @Test
    void returnsNotFoundForMissingToken() {
        Scenario scenario = Scenario.ready();
        scenario.readPort.row = null;

        ReservationPublicShareResult result = scenario.service.getPublicShare("missing-token");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationPublicShareError.TOKEN_NOT_FOUND);
    }

    @Test
    void returnsRevokedForRevokedToken() {
        Scenario scenario = Scenario.ready();
        scenario.readPort.row = row("revoked", null, "A01");

        ReservationPublicShareResult result = scenario.service.getPublicShare("revoked-token");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationPublicShareError.TOKEN_REVOKED);
    }

    @Test
    void returnsExpiredForExpiredToken() {
        Scenario scenario = Scenario.ready();
        scenario.readPort.row = row("active", Instant.parse("2030-06-17T23:59:59Z"), "A01");

        ReservationPublicShareResult result = scenario.service.getPublicShare("expired-token");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationPublicShareError.TOKEN_EXPIRED);
    }

    @Test
    void displaysPendingTableWhenNoTableCodeExists() {
        Scenario scenario = Scenario.ready();
        scenario.readPort.row = row("active", null, null);

        ReservationPublicShareResult result = scenario.service.getPublicShare("pending-table-token");

        assertThat(result.success()).isTrue();
        assertThat(result.share().tableCode()).isEqualTo("待确认");
        assertThat(result.share().tablePending()).isTrue();
    }

    private static ReservationPublicShareRow row(String status, Instant expiresAt, String tableCode) {
        return new ReservationPublicShareRow(
            "share-token-1",
            status,
            expiresAt,
            RESERVATION_ID,
            "R-PUBLIC-0007",
            4,
            Instant.parse("2030-06-20T03:30:00Z"),
            tableCode,
            "Reservation Store",
            "Asia/Singapore",
            "食刻订位中心",
            "1 Example Road",
            "https://maps.app.goo.gl/rpb",
            "6333 1234",
            "请提前 10 分钟到店"
        );
    }

    private static final class Scenario {
        private final FakeReservationPublicShareReadPort readPort = new FakeReservationPublicShareReadPort();
        private final ReservationPublicShareApplicationService service = new ReservationPublicShareApplicationService(
            readPort,
            new StoreShareDateTimeFormatter(),
            () -> NOW
        );

        private static Scenario ready() {
            Scenario scenario = new Scenario();
            scenario.readPort.row = row("active", null, "A01");
            return scenario;
        }
    }

    private static final class FakeReservationPublicShareReadPort implements ReservationPublicShareReadPort {
        private ReservationPublicShareRow row;

        @Override
        public Optional<ReservationPublicShareRow> findByToken(String token) {
            return Optional.ofNullable(row);
        }
    }
}
