package com.rpb.reservation.appgate.application;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.appgate.persistence.entity.*;
import com.rpb.reservation.appgate.persistence.repository.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoreSubscriptionAppGateTest {
    final UUID tenant = UUID.randomUUID(), store = UUID.randomUUID(), sibling = UUID.randomUUID();
    final OffsetDateTime now = OffsetDateTime.parse("2026-10-22T04:00:00Z");
    final PlatformAppJpaRepository apps = mock(PlatformAppJpaRepository.class);
    final TenantAppEntitlementJpaRepository tenants = mock(TenantAppEntitlementJpaRepository.class);
    final StoreAppSettingJpaRepository settings = mock(StoreAppSettingJpaRepository.class);
    final StoreAppSubscriptionRepository subscriptions = mock(StoreAppSubscriptionRepository.class);
    final AppGateService gate = new AppGateService(apps, tenants, settings, subscriptions,
        Clock.fixed(now.toInstant(), ZoneOffset.UTC));
    final CurrentActor actor = CurrentActor.storeStaff(tenant, UUID.randomUUID(), "staff", Set.of("store_staff"),
        Set.of("daily_ledger.view"), Set.of(store, sibling));

    @BeforeEach void setup() {
        var app = PlatformAppEntity.of(UUID.randomUUID(), "daily_ledger", "营业记录", "active",
            "/stores/:storeId/daily-ledger", null, 40, "{}");
        var entitlement = TenantAppEntitlementEntity.of(UUID.randomUUID(), tenant, "daily_ledger", "enabled",
            now.minusMonths(1), now.plusMonths(2), "{}", null, null);
        when(apps.findByAppKey("daily_ledger")).thenReturn(Optional.of(app));
        when(apps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active")).thenReturn(List.of(app));
        when(tenants.findByTenantIdAndAppKey(tenant, "daily_ledger")).thenReturn(Optional.of(entitlement));
        when(tenants.findAllByTenantId(tenant)).thenReturn(List.of(entitlement));
        for (UUID id : List.of(store, sibling)) {
            var setting = StoreAppSettingEntity.of(UUID.randomUUID(), tenant, id, "daily_ledger", true, true, "{}", null, null, null);
            when(settings.findByTenantIdAndStoreIdAndAppKey(tenant, id, "daily_ledger")).thenReturn(Optional.of(setting));
            when(settings.findAllByTenantIdAndStoreId(tenant, id)).thenReturn(List.of(setting));
        }
    }

    AppGateDecision evaluate(UUID id) {
        return gate.evaluate(new AppGateAccessRequest("daily_ledger", tenant, id, "daily_ledger.view", actor));
    }

    void state(UUID id, String status, OffsetDateTime end) {
        when(subscriptions.findByTenantAndStore(tenant, id)).thenReturn(Map.of("daily_ledger", new StoreAppSubscription("active", status, end)));
    }

    @Test void expiredStoreDeniedAndHiddenWhileSiblingRemainsUsable() {
        state(store, "active", now.minusSeconds(1)); state(sibling, "active", now.plusDays(2));
        var decision = evaluate(store);
        assertThat(decision.denyReason()).isEqualTo(AppGateDenyReason.STORE_APP_EXPIRED);
        assertThat(gate.visibleApps(actor, store)).isEmpty();
        assertThat(evaluate(sibling).allowed()).isTrue();
        assertThat(gate.visibleApps(actor, sibling)).hasSize(1);
        assertThat(new AppGateApiErrorMapper().toResponse(decision).getStatusCode().value()).isEqualTo(403);
        assertThat(decision.messageKey()).isEqualTo("appgate.store_app_expired");
    }

    @Test void exactExpiryIsDeniedAndRenewalRestoresBothApiAndMenu() {
        state(store, "active", now);
        assertThat(evaluate(store).denyReason()).isEqualTo(AppGateDenyReason.STORE_APP_EXPIRED);
        state(store, "active", now.plusMonths(1));
        assertThat(evaluate(store).allowed()).isTrue();
        assertThat(gate.visibleApps(actor, store)).hasSize(1);
    }

    @Test void missingSuspendedCancelledOrExpiredStoreNeverFallsBackToTenantGrant() {
        for (String status : Arrays.asList(null, "suspended", "cancelled", "expired")) {
            state(store, status, now.plusDays(1));
            assertThat(evaluate(store).allowed()).isFalse();
            assertThat(gate.visibleApps(actor, store)).isEmpty();
        }
    }

    @Test void historicalTenantOnlyGrantAndOpenEndedStoreRemainUsable() {
        when(subscriptions.findByTenantAndStore(tenant, store)).thenReturn(Map.of());
        assertThat(evaluate(store).allowed()).isTrue();
        state(store, "active", null);
        assertThat(evaluate(store).allowed()).isTrue();
    }

    @Test void actorTenantCannotBeReplacedEvenWhenStoreIdIsInActorAccessSet() {
        var foreignActor = CurrentActor.storeStaff(UUID.randomUUID(), UUID.randomUUID(), "staff", Set.of("store_staff"),
            Set.of("daily_ledger.view"), Set.of(store));
        assertThat(gate.evaluate(new AppGateAccessRequest("daily_ledger", tenant, store, "daily_ledger.view", foreignActor)).allowed()).isFalse();
    }

    @Test void privateConfigCannotTurnAnUnregisteredProductIntoExecutableEntry() {
        var unregistered = PlatformAppEntity.of(UUID.randomUUID(), "unregistered", "unknown", "active",
            "/stores/:storeId/daily-ledger", null, 40, "{\"entryPermissions\":[\"daily_ledger.view\"]}");
        when(apps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active")).thenReturn(List.of(unregistered));
        when(tenants.findAllByTenantId(tenant)).thenReturn(List.of(TenantAppEntitlementEntity.of(UUID.randomUUID(), tenant,
            "unregistered", "enabled", null, null, "{}", null, null)));
        when(settings.findAllByTenantIdAndStoreId(tenant, store)).thenReturn(List.of(StoreAppSettingEntity.of(UUID.randomUUID(), tenant,
            store, "unregistered", true, true, "{}", null, null, null)));
        assertThat(gate.visibleApps(actor, store)).isEmpty();
    }
}
