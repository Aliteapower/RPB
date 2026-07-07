package com.rpb.reservation.platformbilling.application;

import com.rpb.reservation.platformbilling.persistence.ProductSubscriptionEventRepository;
import com.rpb.reservation.platformbilling.persistence.ProductSubscriptionItemRepository;
import com.rpb.reservation.platformbilling.persistence.ProductSubscriptionRepository;
import com.rpb.reservation.platformbilling.persistence.TenantProductEntitlementSyncGateway;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductSubscriptionService {
    private static final String ACTIVE = "active";
    private static final String SUSPENDED = "suspended";
    private static final String CANCELLED = "cancelled";
    private static final String LEGACY_GRANT = "legacy_grant";
    private static final Set<String> PURCHASE_CYCLES = Set.of("monthly", "yearly", "manual");
    private static final Set<String> CONVERT_CYCLES = Set.of("monthly", "yearly");

    private final ProductSubscriptionRepository subscriptions;
    private final ProductSubscriptionEventRepository events;
    private final ProductSubscriptionItemRepository items;
    private final TenantProductEntitlementSyncGateway entitlements;
    private final SubscriptionQuoteService quoteService;
    private final BillingPeriodCalculator periodCalculator;
    private final Clock clock;

    public ProductSubscriptionService(
        ProductSubscriptionRepository subscriptions,
        ProductSubscriptionEventRepository events,
        ProductSubscriptionItemRepository items,
        TenantProductEntitlementSyncGateway entitlements,
        SubscriptionQuoteService quoteService,
        BillingPeriodCalculator periodCalculator,
        Clock clock
    ) {
        this.subscriptions = subscriptions;
        this.events = events;
        this.items = items;
        this.entitlements = entitlements;
        this.quoteService = quoteService;
        this.periodCalculator = periodCalculator;
        this.clock = clock;
    }

    public List<ProductSubscription> listSubscriptions(UUID tenantId) {
        ensureTenant(tenantId);
        return attachItems(tenantId, subscriptions.listByTenantId(tenantId)).stream()
            .map(ProductSubscriptionService::withEffectiveStatus)
            .toList();
    }

    @Transactional
    public ProductSubscriptionMutationResult purchase(
        UUID tenantId,
        ProductSubscriptionCommand command,
        PlatformBillingOperator operator
    ) {
        ensureTenant(tenantId);
        ProductSubscriptionCommand normalized = validateMutation(command, PURCHASE_CYCLES, false);
        ensureProductLine(normalized.appKey());
        Optional<ProductSubscriptionMutationResult> replay = replay(
            tenantId,
            normalized.appKey(),
            "purchase",
            normalized.idempotencyKey()
        );
        if (replay.isPresent()) {
            return replay.get();
        }
        if (subscriptions.findByTenantIdAndAppKey(tenantId, normalized.appKey()).isPresent()) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.SUBSCRIPTION_CONFLICT);
        }
        PreparedMutation prepared = prepareMutation(tenantId, "purchase", null, normalized);

        ProductSubscription created = subscriptions.create(new ProductSubscriptionDraft(
            tenantId,
            prepared.command().appKey(),
            prepared.command().billingCycle(),
            ACTIVE,
            prepared.command().currentPeriodStart(),
            prepared.command().currentPeriodEnd(),
            prepared.command().amount(),
            prepared.command().currency(),
            prepared.command().paymentNote(),
            operator.userId()
        ));
        ProductSubscription createdWithItems = replaceStoreItems(created, prepared);
        appendEvent(created, "purchase", normalized.idempotencyKey(), operator.userId(), prepared.quote());
        entitlements.enableTenantApp(
            tenantId,
            created.appKey(),
            created.currentPeriodStart(),
            created.currentPeriodEnd(),
            operator.userId()
        );
        return new ProductSubscriptionMutationResult(false, afterEnabledSync(createdWithItems), prepared.quote());
    }

    @Transactional
    public ProductSubscriptionMutationResult renew(
        UUID tenantId,
        UUID subscriptionId,
        ProductSubscriptionCommand command,
        PlatformBillingOperator operator
    ) {
        ProductSubscription current = currentSubscription(tenantId, subscriptionId);
        ProductSubscriptionCommand normalized = validateMutation(command, PURCHASE_CYCLES, false);
        if (!current.appKey().equals(normalized.appKey())) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        Optional<ProductSubscriptionMutationResult> replay = replay(tenantId, current.appKey(), "renew", normalized.idempotencyKey());
        if (replay.isPresent()) {
            return replay.get();
        }
        checkVersion(current, normalized.version());
        if (current.currentPeriodEnd() != null
            && normalized.currentPeriodEnd() != null
            && !normalized.currentPeriodEnd().isAfter(current.currentPeriodEnd())) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.SUBSCRIPTION_CONFLICT);
        }
        PreparedMutation prepared = prepareMutation(tenantId, "renew", current, normalized);

        ProductSubscription updated = subscriptions.update(new ProductSubscriptionUpdate(
            tenantId,
            subscriptionId,
            prepared.command().billingCycle(),
            ACTIVE,
            prepared.command().currentPeriodStart(),
            prepared.command().currentPeriodEnd(),
            prepared.command().amount(),
            prepared.command().currency(),
            prepared.command().paymentNote(),
            operator.userId(),
            current.version()
        ));
        ProductSubscription updatedWithItems = replaceStoreItems(updated, prepared);
        appendEvent(updated, "renew", normalized.idempotencyKey(), operator.userId(), prepared.quote());
        entitlements.enableTenantApp(tenantId, updated.appKey(), updated.currentPeriodStart(), updated.currentPeriodEnd(), operator.userId());
        return new ProductSubscriptionMutationResult(false, afterEnabledSync(updatedWithItems), prepared.quote());
    }

    @Transactional
    public ProductSubscriptionMutationResult suspend(
        UUID tenantId,
        UUID subscriptionId,
        ProductSubscriptionStatusCommand command,
        PlatformBillingOperator operator
    ) {
        return updateStatus(tenantId, subscriptionId, command, operator, SUSPENDED, "suspend");
    }

    @Transactional
    public ProductSubscriptionMutationResult cancel(
        UUID tenantId,
        UUID subscriptionId,
        ProductSubscriptionStatusCommand command,
        PlatformBillingOperator operator
    ) {
        return updateStatus(tenantId, subscriptionId, command, operator, CANCELLED, "cancel");
    }

    @Transactional
    public ProductSubscriptionMutationResult convertFromLegacy(
        UUID tenantId,
        UUID subscriptionId,
        ProductSubscriptionCommand command,
        PlatformBillingOperator operator
    ) {
        ProductSubscription current = currentSubscription(tenantId, subscriptionId);
        if (!LEGACY_GRANT.equals(current.billingCycle())) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.SUBSCRIPTION_CONFLICT);
        }
        ProductSubscriptionCommand normalized = validateMutation(command, CONVERT_CYCLES, false);
        if (!current.appKey().equals(normalized.appKey())) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        Optional<ProductSubscriptionMutationResult> replay = replay(
            tenantId,
            current.appKey(),
            "convert_from_legacy",
            normalized.idempotencyKey()
        );
        if (replay.isPresent()) {
            return replay.get();
        }
        checkVersion(current, normalized.version());
        PreparedMutation prepared = prepareMutation(tenantId, "convert_from_legacy", current, normalized);
        ProductSubscription updated = subscriptions.update(new ProductSubscriptionUpdate(
            tenantId,
            subscriptionId,
            prepared.command().billingCycle(),
            ACTIVE,
            prepared.command().currentPeriodStart(),
            prepared.command().currentPeriodEnd(),
            prepared.command().amount(),
            prepared.command().currency(),
            prepared.command().paymentNote(),
            operator.userId(),
            current.version()
        ));
        ProductSubscription updatedWithItems = replaceStoreItems(updated, prepared);
        appendEvent(updated, "convert_from_legacy", normalized.idempotencyKey(), operator.userId(), prepared.quote());
        entitlements.enableTenantApp(tenantId, updated.appKey(), updated.currentPeriodStart(), updated.currentPeriodEnd(), operator.userId());
        return new ProductSubscriptionMutationResult(false, afterEnabledSync(updatedWithItems), prepared.quote());
    }

    private ProductSubscriptionMutationResult updateStatus(
        UUID tenantId,
        UUID subscriptionId,
        ProductSubscriptionStatusCommand command,
        PlatformBillingOperator operator,
        String nextStatus,
        String eventType
    ) {
        ProductSubscription current = currentSubscription(tenantId, subscriptionId);
        ProductSubscriptionStatusCommand normalized = validateStatus(command);
        Optional<ProductSubscriptionMutationResult> replay = replay(tenantId, current.appKey(), eventType, normalized.idempotencyKey());
        if (replay.isPresent()) {
            return replay.get();
        }
        checkVersion(current, normalized.version());
        ProductSubscription updated = subscriptions.update(new ProductSubscriptionUpdate(
            tenantId,
            subscriptionId,
            current.billingCycle(),
            nextStatus,
            current.currentPeriodStart(),
            current.currentPeriodEnd(),
            current.amount(),
            current.currency(),
            normalized.paymentNote(),
            operator.userId(),
            current.version()
        ));
        items.updateStatus(tenantId, subscriptionId, nextStatus);
        ProductSubscription updatedWithItems = withItems(updated);
        appendEvent(updated, eventType, normalized.idempotencyKey(), operator.userId(), null);
        if (SUSPENDED.equals(nextStatus)) {
            entitlements.suspendTenantApp(tenantId, updated.appKey(), operator.userId());
        } else {
            entitlements.disableTenantApp(tenantId, updated.appKey(), operator.userId());
        }
        String entitlementStatus = SUSPENDED.equals(nextStatus) ? "suspended" : "disabled";
        return new ProductSubscriptionMutationResult(false, withEffectiveStatus(
            updatedWithItems.withEntitlementState(entitlementStatus, updated.entitlementValidUntil())
        ));
    }

    private Optional<ProductSubscriptionMutationResult> replay(
        UUID tenantId,
        String appKey,
        String eventType,
        String idempotencyKey
    ) {
        return events.findSubscriptionIdByIdempotencyKey(tenantId, appKey, eventType, idempotencyKey)
            .flatMap(subscriptionId -> subscriptions.findByTenantIdAndId(tenantId, subscriptionId))
            .map(subscription -> new ProductSubscriptionMutationResult(true, withEffectiveStatus(withItems(subscription))));
    }

    private ProductSubscription currentSubscription(UUID tenantId, UUID subscriptionId) {
        ensureTenant(tenantId);
        if (subscriptionId == null) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        return subscriptions.findByTenantIdAndId(tenantId, subscriptionId)
            .orElseThrow(() -> new PlatformBillingServiceException(PlatformBillingServiceErrorCode.SUBSCRIPTION_NOT_FOUND));
    }

    private void ensureTenant(UUID tenantId) {
        if (tenantId == null || !subscriptions.existsActiveTenant(tenantId)) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.TENANT_NOT_FOUND);
        }
    }

    private void ensureProductLine(String appKey) {
        subscriptions.findProductLine(appKey)
            .orElseThrow(() -> new PlatformBillingServiceException(PlatformBillingServiceErrorCode.PRODUCT_LINE_NOT_FOUND));
    }

    private static ProductSubscriptionCommand validateMutation(
        ProductSubscriptionCommand command,
        Set<String> allowedCycles,
        boolean allowLegacyGrant
    ) {
        if (command == null || blank(command.idempotencyKey()) || blank(command.appKey()) || blank(command.billingCycle())) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        String cycle = command.billingCycle().trim();
        if (!allowedCycles.contains(cycle) && !(allowLegacyGrant && LEGACY_GRANT.equals(cycle))) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        if ((cycle.equals("monthly") || cycle.equals("yearly"))
            && command.durationCount() == null
            && (command.currentPeriodStart() == null || command.currentPeriodEnd() == null)) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        if (command.currentPeriodStart() != null
            && command.currentPeriodEnd() != null
            && !command.currentPeriodEnd().isAfter(command.currentPeriodStart())) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        BigDecimal amount = command.amount();
        if (amount == null && command.durationCount() == null) {
            amount = BigDecimal.ZERO;
        }
        if (amount != null && amount.signum() < 0) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        String currency = blank(command.currency()) ? "SGD" : command.currency().trim().toUpperCase();
        if (currency.length() != 3) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        return new ProductSubscriptionCommand(
            command.idempotencyKey().trim(),
            command.appKey().trim(),
            cycle,
            command.currentPeriodStart(),
            command.currentPeriodEnd(),
            amount,
            currency,
            textOrNull(command.paymentNote()),
            command.durationCount(),
            command.version()
        );
    }

    private static ProductSubscriptionStatusCommand validateStatus(ProductSubscriptionStatusCommand command) {
        if (command == null || blank(command.idempotencyKey())) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        return new ProductSubscriptionStatusCommand(
            command.idempotencyKey().trim(),
            textOrNull(command.paymentNote()),
            command.version()
        );
    }

    private static void checkVersion(ProductSubscription subscription, Integer version) {
        if (version != null && subscription.version() != version) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.VERSION_CONFLICT);
        }
    }

    private PreparedMutation prepareMutation(
        UUID tenantId,
        String operation,
        ProductSubscription current,
        ProductSubscriptionCommand command
    ) {
        if (command.durationCount() == null) {
            return new PreparedMutation(command, null, List.of());
        }
        List<BillableStore> billableStores = items.listActiveStores(tenantId);
        if (billableStores.isEmpty()) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        BillingDuration duration = new BillingDuration(command.billingCycle(), command.durationCount());
        BillingPeriodCalculation period = periodCalculator.calculate(
            operation,
            current,
            duration,
            OffsetDateTime.now(clock)
        );
        SubscriptionQuote quote = quoteService.quote(
            command.appKey(),
            duration,
            billableStores.size(),
            command.amount(),
            command.currency()
        );
        ProductSubscriptionCommand prepared = new ProductSubscriptionCommand(
            command.idempotencyKey(),
            command.appKey(),
            command.billingCycle(),
            period.periodStart(),
            period.periodEnd(),
            quote.finalAmount(),
            quote.currency(),
            command.paymentNote(),
            command.durationCount(),
            command.version()
        );
        return new PreparedMutation(prepared, quote, billableStores);
    }

    private ProductSubscription replaceStoreItems(ProductSubscription subscription, PreparedMutation prepared) {
        items.replaceStoreItems(subscription, prepared.billableStores(), prepared.quote());
        return withItems(subscription);
    }

    private ProductSubscription withItems(ProductSubscription subscription) {
        List<ProductSubscriptionItem> subscriptionItems = items.listByTenantId(subscription.tenantId()).stream()
            .filter(item -> item.subscriptionId().equals(subscription.id()))
            .toList();
        return subscription.withItems(subscriptionItems);
    }

    private List<ProductSubscription> attachItems(UUID tenantId, List<ProductSubscription> subscriptions) {
        Map<UUID, List<ProductSubscriptionItem>> bySubscriptionId = items.listByTenantId(tenantId).stream()
            .collect(Collectors.groupingBy(ProductSubscriptionItem::subscriptionId));
        return subscriptions.stream()
            .map(subscription -> subscription.withItems(bySubscriptionId.getOrDefault(subscription.id(), List.of())))
            .toList();
    }

    private void appendEvent(
        ProductSubscription subscription,
        String eventType,
        String idempotencyKey,
        UUID operatorUserId,
        SubscriptionQuote quote
    ) {
        events.append(new ProductSubscriptionEventDraft(
            subscription.id(),
            subscription.tenantId(),
            subscription.appKey(),
            eventType,
            subscription.billingCycle(),
            subscription.status(),
            subscription.currentPeriodStart(),
            subscription.currentPeriodEnd(),
            subscription.amount(),
            subscription.currency(),
            subscription.paymentNote(),
            idempotencyKey,
            operatorUserId,
            quote == null ? "{}" : quote.eventPayloadJson()
        ));
    }

    private record PreparedMutation(
        ProductSubscriptionCommand command,
        SubscriptionQuote quote,
        List<BillableStore> billableStores
    ) {
    }

    private static ProductSubscription withEffectiveStatus(ProductSubscription subscription) {
        if (ACTIVE.equals(subscription.status())
            && subscription.currentPeriodEnd() != null
            && !subscription.currentPeriodEnd().isAfter(OffsetDateTime.now())) {
            return subscription.withEffectiveStatus("expired");
        }
        return subscription.withEffectiveStatus(subscription.status());
    }

    private static ProductSubscription afterEnabledSync(ProductSubscription subscription) {
        return withEffectiveStatus(subscription.withEntitlementState("enabled", subscription.currentPeriodEnd()));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String textOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
