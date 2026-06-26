package com.rpb.reservation.platformbilling.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.platformbilling.persistence.ProductSubscriptionEventRepository;
import com.rpb.reservation.platformbilling.persistence.ProductSubscriptionRepository;
import com.rpb.reservation.platformbilling.persistence.PlatformProductLinePriceRepository;
import com.rpb.reservation.platformbilling.persistence.TenantProductEntitlementSyncGateway;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductSubscriptionServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000009201");
    private static final UUID OPERATOR_ID = UUID.fromString("30000000-0000-0000-0000-000000009201");
    private static final String APP_KEY = "reservation_queue";
    private static final PlatformBillingOperator OPERATOR = new PlatformBillingOperator(OPERATOR_ID, "platform_admin");
    private static final OffsetDateTime JAN_1 = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    private static final OffsetDateTime JAN_31 = OffsetDateTime.parse("2026-01-31T23:59:59Z");
    private static final OffsetDateTime FEB_28 = OffsetDateTime.parse("2026-02-28T23:59:59Z");
    private static final Clock CLOCK = Clock.fixed(JAN_1.toInstant(), ZoneOffset.UTC);

    private FakeProductSubscriptionRepository subscriptions;
    private FakeProductSubscriptionEventRepository events;
    private FakeProductLinePriceRepository prices;
    private FakeEntitlementSyncGateway syncGateway;
    private ProductSubscriptionService service;

    @BeforeEach
    void setUp() {
        subscriptions = new FakeProductSubscriptionRepository();
        events = new FakeProductSubscriptionEventRepository();
        prices = new FakeProductLinePriceRepository();
        syncGateway = new FakeEntitlementSyncGateway();
        service = new ProductSubscriptionService(
            subscriptions,
            events,
            syncGateway,
            new SubscriptionQuoteService(prices),
            new BillingPeriodCalculator(),
            CLOCK
        );
    }

    @Test
    void purchaseCreatesActiveSubscriptionEnablesEntitlementAndReplaysDuplicateIdempotencyKey() {
        ProductSubscriptionCommand command = command("purchase-001", "monthly", JAN_1, JAN_31);

        ProductSubscriptionMutationResult first = service.purchase(TENANT_ID, command, OPERATOR);
        ProductSubscriptionMutationResult replay = service.purchase(TENANT_ID, command, OPERATOR);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.subscription().id()).isEqualTo(first.subscription().id());
        assertThat(first.subscription().status()).isEqualTo("active");
        assertThat(first.subscription().billingCycle()).isEqualTo("monthly");
        assertThat(first.subscription().entitlementStatus()).isEqualTo("enabled");
        assertThat(first.subscription().entitlementValidUntil()).isEqualTo(JAN_31);
        assertThat(syncGateway.actions).containsExactly("enabled:reservation_queue:2026-01-31T23:59:59Z");
        assertThat(events.events).containsExactly("purchase:purchase-001");
    }

    @Test
    void renewExtendsCurrentPeriodAndSynchronizesEnabledEntitlement() {
        ProductSubscription existing = subscriptions.add(existingSubscription("monthly", "active", JAN_1, JAN_31, 0));

        ProductSubscriptionMutationResult result = service.renew(
            TENANT_ID,
            existing.id(),
            command("renew-001", "monthly", JAN_1, FEB_28, 0),
            OPERATOR
        );

        assertThat(result.subscription().currentPeriodEnd()).isEqualTo(FEB_28);
        assertThat(result.subscription().version()).isEqualTo(1);
        assertThat(result.subscription().entitlementStatus()).isEqualTo("enabled");
        assertThat(result.subscription().entitlementValidUntil()).isEqualTo(FEB_28);
        assertThat(syncGateway.actions).containsExactly("enabled:reservation_queue:2026-02-28T23:59:59Z");
        assertThat(events.events).containsExactly("renew:renew-001");
    }

    @Test
    void purchaseWithDurationDefaultsAmountFromProductLinePriceAndCalculatesPeriodOnBackend() {
        ProductSubscriptionCommand command = durationCommand("purchase-duration", "monthly", 3, null, null);

        ProductSubscriptionMutationResult result = service.purchase(TENANT_ID, command, OPERATOR);

        assertThat(result.subscription().currentPeriodStart()).isEqualTo(JAN_1);
        assertThat(result.subscription().currentPeriodEnd()).isEqualTo(OffsetDateTime.parse("2026-04-01T00:00:00Z"));
        assertThat(result.subscription().amount()).isEqualByComparingTo("384.00");
        assertThat(result.quote().durationCount()).isEqualTo(3);
        assertThat(result.quote().unitAmount()).isEqualByComparingTo("128.00");
        assertThat(result.quote().defaultAmount()).isEqualByComparingTo("384.00");
        assertThat(events.payloads).singleElement().satisfies(payload -> assertThat(payload)
            .contains("\"durationCount\": 3")
            .contains("\"priceSource\": \"platform_product_line_prices\""));
    }

    @Test
    void renewWithDurationExtendsFromFutureCurrentEndAndAllowsManualAmountOverride() {
        ProductSubscription existing = subscriptions.add(existingSubscription("monthly", "active", JAN_1, JAN_31, 0));

        ProductSubscriptionMutationResult result = service.renew(
            TENANT_ID,
            existing.id(),
            durationCommand("renew-duration", "monthly", 2, new BigDecimal("200.00"), 0),
            OPERATOR
        );

        assertThat(result.subscription().currentPeriodStart()).isEqualTo(JAN_31);
        assertThat(result.subscription().currentPeriodEnd()).isEqualTo(OffsetDateTime.parse("2026-03-31T23:59:59Z"));
        assertThat(result.subscription().amount()).isEqualByComparingTo("200.00");
        assertThat(result.quote().defaultAmount()).isEqualByComparingTo("256.00");
        assertThat(result.quote().finalAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void suspendAndCancelOnlyChangeEntitlementStatusThroughAppGateSyncBoundary() {
        ProductSubscription existing = subscriptions.add(existingSubscription("monthly", "active", JAN_1, JAN_31, 0));

        ProductSubscriptionMutationResult suspended = service.suspend(
            TENANT_ID,
            existing.id(),
            new ProductSubscriptionStatusCommand("suspend-001", "欠费暂停", 0),
            OPERATOR
        );
        ProductSubscriptionMutationResult cancelled = service.cancel(
            TENANT_ID,
            existing.id(),
            new ProductSubscriptionStatusCommand("cancel-001", "客户取消", 1),
            OPERATOR
        );

        assertThat(suspended.subscription().status()).isEqualTo("suspended");
        assertThat(suspended.subscription().entitlementStatus()).isEqualTo("suspended");
        assertThat(cancelled.subscription().status()).isEqualTo("cancelled");
        assertThat(cancelled.subscription().entitlementStatus()).isEqualTo("disabled");
        assertThat(syncGateway.actions).containsExactly(
            "suspended:reservation_queue",
            "disabled:reservation_queue"
        );
    }

    @Test
    void convertsLegacyGrantToPaidCycleWithoutCreatingASecondSubscription() {
        ProductSubscription legacy = subscriptions.add(existingSubscription("legacy_grant", "active", JAN_1, null, 0));

        ProductSubscriptionMutationResult result = service.convertFromLegacy(
            TENANT_ID,
            legacy.id(),
            command("convert-001", "yearly", JAN_1, OffsetDateTime.parse("2026-12-31T23:59:59Z"), 0),
            OPERATOR
        );

        assertThat(result.subscription().id()).isEqualTo(legacy.id());
        assertThat(result.subscription().billingCycle()).isEqualTo("yearly");
        assertThat(result.subscription().currentPeriodEnd()).isEqualTo(OffsetDateTime.parse("2026-12-31T23:59:59Z"));
        assertThat(subscriptions.listByTenantId(TENANT_ID)).hasSize(1);
        assertThat(events.events).containsExactly("convert_from_legacy:convert-001");
    }

    @Test
    void rejectsManualCycleWhenConvertingLegacyGrant() {
        ProductSubscription legacy = subscriptions.add(existingSubscription("legacy_grant", "active", JAN_1, null, 0));

        assertThatThrownBy(() -> service.convertFromLegacy(
            TENANT_ID,
            legacy.id(),
            command("convert-manual", "manual", JAN_1, null, 0),
            OPERATOR
        ))
            .isInstanceOf(PlatformBillingServiceException.class)
            .hasMessageContaining(PlatformBillingServiceErrorCode.REQUEST_INVALID.name());

        assertThat(syncGateway.actions).isEmpty();
        assertThat(events.events).isEmpty();
    }

    @Test
    void rejectsDuplicateProductPurchaseAndStaleVersion() {
        ProductSubscription existing = subscriptions.add(existingSubscription("monthly", "active", JAN_1, JAN_31, 2));

        assertThatThrownBy(() -> service.purchase(TENANT_ID, command("purchase-002", "monthly", JAN_1, JAN_31), OPERATOR))
            .isInstanceOf(PlatformBillingServiceException.class)
            .hasMessageContaining(PlatformBillingServiceErrorCode.SUBSCRIPTION_CONFLICT.name());

        assertThatThrownBy(() -> service.renew(TENANT_ID, existing.id(), command("renew-stale", "monthly", JAN_1, FEB_28, 1), OPERATOR))
            .isInstanceOf(PlatformBillingServiceException.class)
            .hasMessageContaining(PlatformBillingServiceErrorCode.VERSION_CONFLICT.name());
    }

    private static ProductSubscriptionCommand command(
        String idempotencyKey,
        String billingCycle,
        OffsetDateTime periodStart,
        OffsetDateTime periodEnd
    ) {
        return command(idempotencyKey, billingCycle, periodStart, periodEnd, null);
    }

    private static ProductSubscriptionCommand durationCommand(
        String idempotencyKey,
        String billingCycle,
        int durationCount,
        BigDecimal amount,
        Integer version
    ) {
        return new ProductSubscriptionCommand(
            idempotencyKey,
            APP_KEY,
            billingCycle,
            null,
            null,
            amount,
            "SGD",
            "manual payment",
            durationCount,
            version
        );
    }

    private static ProductSubscriptionCommand command(
        String idempotencyKey,
        String billingCycle,
        OffsetDateTime periodStart,
        OffsetDateTime periodEnd,
        Integer version
    ) {
        return new ProductSubscriptionCommand(
            idempotencyKey,
            APP_KEY,
            billingCycle,
            periodStart,
            periodEnd,
            new BigDecimal("128.00"),
            "SGD",
            "manual payment",
            version
        );
    }

    private static ProductSubscription existingSubscription(
        String billingCycle,
        String status,
        OffsetDateTime periodStart,
        OffsetDateTime periodEnd,
        int version
    ) {
        return new ProductSubscription(
            UUID.randomUUID(),
            TENANT_ID,
            APP_KEY,
            "预约排队叫号产线",
            billingCycle,
            status,
            status,
            periodStart,
            periodEnd,
            new BigDecimal("128.00"),
            "SGD",
            "manual payment",
            "enabled",
            periodEnd,
            OffsetDateTime.parse("2026-06-26T00:00:00Z"),
            OffsetDateTime.parse("2026-06-26T00:00:00Z"),
            version
        );
    }

    private static final class FakeProductSubscriptionRepository implements ProductSubscriptionRepository {
        private final Map<UUID, ProductSubscription> byId = new LinkedHashMap<>();

        @Override
        public boolean existsActiveTenant(UUID tenantId) {
            return TENANT_ID.equals(tenantId);
        }

        @Override
        public Optional<PlatformProductLine> findProductLine(String appKey) {
            if (!APP_KEY.equals(appKey)) {
                return Optional.empty();
            }
            return Optional.of(new PlatformProductLine(
                APP_KEY,
                "预约排队叫号产线",
                "active",
                "/stores/:storeId/staff",
                "预约、排队、叫号一体化产线",
                10,
                OffsetDateTime.parse("2026-06-26T00:00:00Z"),
                OffsetDateTime.parse("2026-06-26T00:00:00Z")
            ));
        }

        @Override
        public List<ProductSubscription> listByTenantId(UUID tenantId) {
            return byId.values().stream().filter(subscription -> subscription.tenantId().equals(tenantId)).toList();
        }

        @Override
        public Optional<ProductSubscription> findByTenantIdAndId(UUID tenantId, UUID subscriptionId) {
            return Optional.ofNullable(byId.get(subscriptionId)).filter(subscription -> subscription.tenantId().equals(tenantId));
        }

        @Override
        public Optional<ProductSubscription> findByTenantIdAndAppKey(UUID tenantId, String appKey) {
            return byId.values().stream()
                .filter(subscription -> subscription.tenantId().equals(tenantId))
                .filter(subscription -> subscription.appKey().equals(appKey))
                .findFirst();
        }

        @Override
        public ProductSubscription create(ProductSubscriptionDraft draft) {
            ProductSubscription subscription = new ProductSubscription(
                UUID.randomUUID(),
                draft.tenantId(),
                draft.appKey(),
                "预约排队叫号产线",
                draft.billingCycle(),
                draft.status(),
                draft.status(),
                draft.currentPeriodStart(),
                draft.currentPeriodEnd(),
                draft.amount(),
                draft.currency(),
                draft.paymentNote(),
                draft.status().equals("active") ? "enabled" : draft.status(),
                draft.currentPeriodEnd(),
                OffsetDateTime.parse("2026-06-26T00:00:00Z"),
                OffsetDateTime.parse("2026-06-26T00:00:00Z"),
                0
            );
            byId.put(subscription.id(), subscription);
            return subscription;
        }

        @Override
        public ProductSubscription update(ProductSubscriptionUpdate update) {
            ProductSubscription current = byId.get(update.subscriptionId());
            ProductSubscription next = current.withCommercialState(
                update.billingCycle(),
                update.status(),
                update.currentPeriodStart(),
                update.currentPeriodEnd(),
                update.amount(),
                update.currency(),
                update.paymentNote()
            );
            byId.put(next.id(), next);
            return next;
        }

        ProductSubscription add(ProductSubscription subscription) {
            byId.put(subscription.id(), subscription);
            return subscription;
        }
    }

    private static final class FakeProductSubscriptionEventRepository implements ProductSubscriptionEventRepository {
        private final Map<String, UUID> idempotency = new LinkedHashMap<>();
        private final List<String> events = new ArrayList<>();
        private final List<String> payloads = new ArrayList<>();

        @Override
        public Optional<UUID> findSubscriptionIdByIdempotencyKey(UUID tenantId, String appKey, String eventType, String idempotencyKey) {
            return Optional.ofNullable(idempotency.get(eventType + ":" + idempotencyKey));
        }

        @Override
        public void append(ProductSubscriptionEventDraft draft) {
            events.add(draft.eventType() + ":" + draft.idempotencyKey());
            payloads.add(draft.eventPayloadJson());
            idempotency.put(draft.eventType() + ":" + draft.idempotencyKey(), draft.subscriptionId());
        }
    }

    private static final class FakeProductLinePriceRepository implements PlatformProductLinePriceRepository {
        @Override
        public List<PlatformProductLinePrice> findByAppKeys(Collection<String> appKeys) {
            return List.of(
                new PlatformProductLinePrice(APP_KEY, "monthly", new BigDecimal("128.00"), "SGD", "active", 0),
                new PlatformProductLinePrice(APP_KEY, "yearly", new BigDecimal("1200.00"), "SGD", "active", 0)
            );
        }

        @Override
        public List<PlatformProductLinePrice> replacePrices(String appKey, List<PlatformProductLinePriceUpdate> prices) {
            return findByAppKeys(List.of(appKey));
        }

        @Override
        public Optional<PlatformProductLinePrice> findActivePrice(String appKey, String billingCycle) {
            return findByAppKeys(List.of(appKey)).stream()
                .filter(price -> price.billingCycle().equals(billingCycle))
                .findFirst();
        }
    }

    private static final class FakeEntitlementSyncGateway implements TenantProductEntitlementSyncGateway {
        private final List<String> actions = new ArrayList<>();

        @Override
        public void enableTenantApp(UUID tenantId, String appKey, OffsetDateTime validFrom, OffsetDateTime validUntil, UUID operatorUserId) {
            actions.add("enabled:" + appKey + ":" + validUntil);
        }

        @Override
        public void suspendTenantApp(UUID tenantId, String appKey, UUID operatorUserId) {
            actions.add("suspended:" + appKey);
        }

        @Override
        public void disableTenantApp(UUID tenantId, String appKey, UUID operatorUserId) {
            actions.add("disabled:" + appKey);
        }
    }
}
