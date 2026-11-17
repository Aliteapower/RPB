package com.rpb.reservation.cards;

import com.rpb.reservation.cards.application.CardAccess;
import com.rpb.reservation.wallet.application.WalletAccess;
import com.rpb.reservation.cards.voucher.*;
import com.rpb.reservation.catalog.CatalogPostgresTest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoucherPostgresTest extends CatalogPostgresTest {
    private static final Instant NOW = Instant.parse("2026-11-17T04:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-11-17");

    private VoucherService service(Instant now) {
        var wallet = mock(WalletAccess.class, RETURNS_DEEP_STUBS);
        when(wallet.commercial(scope).allowed()).thenReturn(true);
        var access = mock(CardAccess.class);
        when(access.settingsEnabled(scope)).thenReturn(true);
        return new VoucherService(new VoucherRepository(jdbc, json), access, products, Clock.fixed(now, ZoneOffset.UTC), wallet);
    }

    @Test void sameIpCannotClaimAgainAndReceiptRecoversOriginalFaceAfterEditing() {
        var service = service(NOW);
        var draft = tx.execute(t -> service.save(scope, actor, null, cash(1000, DAY, null)));
        var active = tx.execute(t -> service.state(scope, actor, draft.id(), "active", draft.version()));
        String receipt = key();
        var first = tx.execute(t -> service.claim(draft.id(), "192.0.2.14", receipt));
        assertThat(first.status()).isEqualTo("claimed");
        assertThat(first.batchCode()).isEqualTo(draft.activityCode());
        var duplicate = tx.execute(t -> service.claim(draft.id(), "192.0.2.14", key()));
        assertThat(duplicate.status()).isEqualTo("already_claimed");
        assertThat(duplicate.code()).isNull();
        tx.execute(t -> service.save(scope, actor, draft.id(), cash(800, DAY, active.version())));
        var second = tx.execute(t -> service.claim(draft.id(), "192.0.2.15", key()));
        assertThat(second.face().valueCents()).isEqualTo(800);
        assertThat(service.view(draft.id(), "192.0.2.14", receipt)).isEqualTo(first);
        var reopened = service.view(draft.id(), "192.0.2.14", null);
        assertThat(reopened.status()).isEqualTo("already_claimed");
        assertThat(reopened.code()).isNull();
        assertThat(reopened.batchCode()).isEqualTo(draft.activityCode());
        assertThat(service.view(draft.id(), "192.0.2.16", null).status()).isEqualTo("open");
        assertThat(service.detail(scope, actor, draft.id()).issued()).isEqualTo(2);
        var expired = tx.execute(t -> service(NOW.plus(Duration.ofDays(1))).redeem(scope, actor, first.code()));
        assertThat(expired.status()).isEqualTo("expired");
        assertThat(service.detail(scope, actor, draft.id()).redeemed()).isZero();
        var foreign = new com.rpb.reservation.common.scope.StoreScope(
            new com.rpb.reservation.tenant.value.TenantId(UUID.randomUUID()), store);
        assertThatThrownBy(() -> tx.execute(t -> service.redeem(foreign, actor, first.code())))
            .hasMessageContaining("券码不存在");
        assertThatThrownBy(() -> tx.execute(t -> service.redeem(
            new com.rpb.reservation.common.scope.StoreScope(scope.tenantId(), UUID.randomUUID()), actor, first.code())))
            .hasMessageContaining("无卡业务操作权限");
    }

    private VoucherDtos.Write cash(long value, LocalDate lastDay, Integer version) {
        return new VoucherDtos.Write("代金券", "cash", null, null, value, 3, List.of(store),
            NOW.minusSeconds(60), NOW.plusSeconds(120), DAY, lastDay, "第一条\n第二条", null, version);
    }

    @Test void voucherReportCountsUnusedExpiryByIssuedRevision() {
        var service = service(NOW);
        var draft = tx.execute(t -> service.save(scope, actor, null, cash(1000, DAY, null)));
        var active = tx.execute(t -> service.state(scope, actor, draft.id(), "active", draft.version()));
        var redeemed = tx.execute(t -> service.claim(draft.id(), "192.0.2.21", key()));
        tx.execute(t -> service.redeem(scope, actor, redeemed.code()));
        tx.execute(t -> service.claim(draft.id(), "192.0.2.22", key()));
        tx.execute(t -> service.save(scope, actor, draft.id(), cash(1000, DAY.plusDays(3), active.version())));
        tx.execute(t -> service.claim(draft.id(), "192.0.2.23", key()));

        var report = service(NOW.plus(Duration.ofDays(1))).report(scope, actor, draft.activityCode(), "cash", 0);
        assertThat(report.items()).singleElement().satisfies(row -> {
            assertThat(row.total()).isEqualTo(3);
            assertThat(row.issued()).isEqualTo(3);
            assertThat(row.redeemed()).isEqualTo(1);
            assertThat(row.expired()).isEqualTo(1);
        });
        assertThat(service.report(scope, actor, draft.activityCode(), "product", 0).items()).isEmpty();
        var otherStore = new com.rpb.reservation.common.scope.StoreScope(scope.tenantId(), UUID.randomUUID());
        assertThat(service.report(otherStore, actor, draft.activityCode(), "cash", 0).items()).isEmpty();
    }

    @Test void concurrentRedemptionsKeepTheirOwnOrdinalAndOriginalTotal() throws Exception {
        var service = service(NOW);
        var draft = tx.execute(t -> service.save(scope, actor, null, cash(1000, DAY, null)));
        var active = tx.execute(t -> service.state(scope, actor, draft.id(), "active", draft.version()));
        var first = tx.execute(t -> service.claim(draft.id(), "192.0.2.11", key()));
        var second = tx.execute(t -> service.claim(draft.id(), "192.0.2.12", key()));
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            var a = threads.submit(() -> {
                gate.await();
                return tx.execute(t -> service.redeem(scope, actor, first.code()));
            });
            var b = threads.submit(() -> {
                gate.await();
                return tx.execute(t -> service.redeem(scope, actor, second.code()));
            });
            gate.countDown();
            var redeemed = a.get(20, TimeUnit.SECONDS);
            var other = b.get(20, TimeUnit.SECONDS);
            assertThat(List.of(redeemed.redemptionNumber(), other.redemptionNumber())).containsExactlyInAnyOrder(1, 2);
            assertThat(redeemed.issueTotal()).isEqualTo(3);
            assertThat(other.issueTotal()).isEqualTo(3);
            var change = cash(1000, DAY, active.version());
            tx.execute(t -> service.save(scope, actor, draft.id(), new VoucherDtos.Write(
                change.name(), change.kind(), null, null, change.valueCents(), 5, change.stores(),
                change.startsAt(), change.endsAt(), change.validFrom(), change.validThrough(),
                change.rules(), null, change.version())));
            var repeat = tx.execute(t -> service.redeem(scope, actor, first.code()));
            assertThat(repeat.status()).isEqualTo("used");
            assertThat(repeat.redemptionNumber()).isEqualTo(redeemed.redemptionNumber());
            assertThat(repeat.issueTotal()).isEqualTo(3);
            assertThat(service.detail(scope, actor, draft.id()).redeemed()).isEqualTo(2);
        }
    }

    @Test void concurrentLastVoucherAndRepeatScan() throws Exception {
        var service = service(NOW);
        var campaign = tx.execute(t -> service.save(scope, actor, null, new VoucherDtos.Write(
            "咖啡抢券", "product", product.id(), 2, 1200L, 1, List.of(store),
            Instant.parse("2026-11-17T00:00:00Z"), Instant.parse("2026-11-18T00:00:00Z"),
            LocalDate.parse("2026-11-17"), LocalDate.parse("2026-11-19"), "测试", null, null)));
        tx.execute(t -> service.state(scope, actor, campaign.id(), "active", campaign.version()));
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var calls = new ArrayList<Future<VoucherDtos.PublicView>>();
            for (int i = 0; i < 12; i++) {
                String ip = "192.0.2." + (i + 1);
                calls.add(threads.submit(() -> {
                    start.await();
                    return tx.execute(t -> service.claim(campaign.id(), ip, key()));
                }));
            }
            start.countDown();
            var results = new ArrayList<VoucherDtos.PublicView>();
            for (var call : calls) results.add(call.get(20, TimeUnit.SECONDS));
            assertThat(results.stream().map(VoucherDtos.PublicView::status))
                .containsOnly("claimed", "sold_out");
            assertThat(results.stream().filter(r -> r.code() != null)).hasSize(1);
            String code = results.stream().filter(r -> r.code() != null).findFirst().orElseThrow().code();
            var redeemed = tx.execute(t -> service.redeem(scope, actor, code));
            assertThat(redeemed.status()).isEqualTo("redeemed");
            assertThat(redeemed.face().quantity()).isEqualTo(2);
            assertThat(redeemed.face().valueCents()).isEqualTo(1200);
            assertThat(redeemed.batchCode()).isEqualTo(campaign.activityCode());
            assertThat(redeemed.redeemedAt()).isEqualTo(NOW);
            var later = service(NOW.plusSeconds(120));
            var repeated = tx.execute(t -> later.redeem(scope, actor, code));
            assertThat(repeated.status()).isEqualTo("used");
            assertThat(repeated.batchCode()).isEqualTo(redeemed.batchCode());
            assertThat(repeated.redeemedAt()).isEqualTo(redeemed.redeemedAt());
            assertThat(service.detail(scope, actor, campaign.id()).redeemed()).isEqualTo(1);
        }
    }
}
