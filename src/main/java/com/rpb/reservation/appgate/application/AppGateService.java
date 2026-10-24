package com.rpb.reservation.appgate.application;

import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.appgate.domain.AppGateDenyReason;
import com.rpb.reservation.common.product.ProductLineEntryRegistry;
import com.rpb.reservation.appgate.persistence.entity.PlatformAppEntity;
import com.rpb.reservation.appgate.persistence.entity.StoreAppSettingEntity;
import com.rpb.reservation.appgate.persistence.entity.TenantAppEntitlementEntity;
import com.rpb.reservation.appgate.persistence.repository.PlatformAppJpaRepository;
import com.rpb.reservation.appgate.persistence.repository.StoreAppSettingJpaRepository;
import com.rpb.reservation.appgate.persistence.repository.TenantAppEntitlementJpaRepository;
import com.rpb.reservation.appgate.persistence.repository.StoreAppSubscriptionRepository;
import com.rpb.reservation.appgate.domain.StoreAppSubscription;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AppGateService {
    private static final String ACTIVE = "active";
    private static final String ENABLED = "enabled";
    private static final String TRIAL = "trial";

    private final PlatformAppJpaRepository platformApps;
    private final TenantAppEntitlementJpaRepository entitlements;
    private final StoreAppSettingJpaRepository storeSettings;
    private final StoreAppSubscriptionRepository storeSubscriptions;
    private final Clock clock;

    public AppGateService(
        PlatformAppJpaRepository platformApps,
        TenantAppEntitlementJpaRepository entitlements,
        StoreAppSettingJpaRepository storeSettings,
        StoreAppSubscriptionRepository storeSubscriptions,
        Clock clock
    ) {
        this.platformApps = platformApps;
        this.entitlements = entitlements;
        this.storeSettings = storeSettings;
        this.storeSubscriptions = storeSubscriptions;
        this.clock = clock;
    }

    public AppGateDecision evaluate(AppGateAccessRequest request) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<PlatformAppEntity> platformApp = platformApps.findByAppKey(request.appKey());
        if (platformApp.isEmpty() || !ACTIVE.equals(platformApp.get().getStatus())) {
            return denied(request, AppGateDenyReason.APP_DISABLED);
        }

        Optional<TenantAppEntitlementEntity> entitlement = entitlements.findByTenantIdAndAppKey(
            request.tenantId(),
            request.appKey()
        );
        if (entitlement.isEmpty()) {
            return denied(request, AppGateDenyReason.TENANT_APP_NOT_ENABLED);
        }
        TenantAppEntitlementEntity tenantApp = entitlement.get();
        if (isExpired(tenantApp, now)) {
            return denied(request, AppGateDenyReason.TENANT_APP_EXPIRED);
        }
        if (!isEnabledOrValidTrial(tenantApp)) {
            return denied(request, AppGateDenyReason.TENANT_APP_NOT_ENABLED);
        }

        Optional<StoreAppSettingEntity> storeSetting = storeSettings.findByTenantIdAndStoreIdAndAppKey(
            request.tenantId(),
            request.storeId(),
            request.appKey()
        );
        if (storeSetting.isEmpty() || !storeSetting.get().isEnabled()) {
            return denied(request, AppGateDenyReason.STORE_APP_NOT_ENABLED);
        }

        CurrentActor actor = request.actor();
        if (actor == null || request.tenantId() == null || !request.tenantId().equals(actor.tenantId()) || !actor.canAccessStore(request.storeId())) {
            return denied(request, AppGateDenyReason.STORE_ACCESS_DENIED);
        }
        if (request.requiredPermission() != null && !request.requiredPermission().isBlank()
            && !actor.hasPermission(request.requiredPermission())) {
            return denied(request, AppGateDenyReason.PERMISSION_DENIED);
        }

        StoreAppSubscription subscription = storeSubscriptions.findByTenantAndStore(request.tenantId(), request.storeId())
            .get(request.appKey());
        AppGateDenyReason storeDenial = storeDenial(request.appKey(), subscription, now);
        if (storeDenial != null) return denied(request, storeDenial);

        return AppGateDecision.allow(request.appKey(), request.tenantId(), request.storeId(), request.requiredPermission());
    }

    /** Customer-facing operations share commercial checks without manufacturing an employee actor. */
    public AppGateDecision evaluateCommercial(String appKey, UUID tenantId, UUID storeId) {
        return evaluateCommercial(appKey, tenantId, storeId, true);
    }

    /** Activation still requires commercial authorization, but intentionally ignores the operational switch. */
    public AppGateDecision evaluateStoreActivation(String appKey, UUID tenantId, UUID storeId) {
        return evaluateCommercial(appKey, tenantId, storeId, false);
    }

    private AppGateDecision evaluateCommercial(String appKey, UUID tenantId, UUID storeId, boolean requireEnabledStore) {
        var request = new AppGateAccessRequest(appKey,tenantId,storeId,null,null);
        OffsetDateTime now = OffsetDateTime.now(clock);
        var app = platformApps.findByAppKey(appKey);
        if(app.isEmpty() || !ACTIVE.equals(app.get().getStatus())) return denied(request,AppGateDenyReason.APP_DISABLED);
        var entitlement = entitlements.findByTenantIdAndAppKey(tenantId,appKey);
        if(entitlement.isEmpty()) return denied(request,AppGateDenyReason.TENANT_APP_NOT_ENABLED);
        if(isExpired(entitlement.get(),now)) return denied(request,AppGateDenyReason.TENANT_APP_EXPIRED);
        if(!isEnabledOrValidTrial(entitlement.get())) return denied(request,AppGateDenyReason.TENANT_APP_NOT_ENABLED);
        if (requireEnabledStore) {
            var setting=storeSettings.findByTenantIdAndStoreIdAndAppKey(tenantId,storeId,appKey);
            if(setting.isEmpty() || !setting.get().isEnabled()) return denied(request,AppGateDenyReason.STORE_APP_NOT_ENABLED);
        }
        var billing=storeSubscriptions.findByTenantAndStore(tenantId,storeId).get(appKey);
        var reason=storeDenial(appKey,billing,now);
        return reason==null ? AppGateDecision.allow(appKey,tenantId,storeId,null) : denied(request,reason);
    }

    public List<AppGateAppEntry> visibleApps(CurrentActor actor, UUID storeId) {
        if (actor == null || actor.tenantId() == null || !actor.canAccessStore(storeId)) {
            return List.of();
        }
        UUID tenantId = actor.tenantId();
        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<String, StoreAppSubscription> storeBilling = storeSubscriptions.findByTenantAndStore(tenantId, storeId);
        Map<String, TenantAppEntitlementEntity> tenantApps = entitlements.findAllByTenantId(tenantId).stream()
            .collect(Collectors.toMap(TenantAppEntitlementEntity::getAppKey, Function.identity(), (left, right) -> left));
        Map<String, StoreAppSettingEntity> storeApps = storeSettings.findAllByTenantIdAndStoreId(tenantId, storeId).stream()
            .collect(Collectors.toMap(StoreAppSettingEntity::getAppKey, Function.identity(), (left, right) -> left));

        return platformApps.findAllByStatusOrderBySortOrderAscAppKeyAsc(ACTIVE).stream()
            .filter(app -> isTenantAppVisible(tenantApps.get(app.getAppKey()), now))
            .filter(app -> isStoreAppVisible(storeApps.get(app.getAppKey())))
            .filter(app -> !app.getDefaultEntryRoute().isBlank() && ProductLineEntryRegistry.isControlledRoute(app.getDefaultEntryRoute()))
            .filter(app -> storeDenial(app.getAppKey(),storeBilling.get(app.getAppKey()),now) == null)
            .filter(app -> hasAnyEntryPermission(actor, app.getAppKey()))
            .sorted(Comparator.comparingInt(PlatformAppEntity::getSortOrder).thenComparing(PlatformAppEntity::getAppKey))
            .map(app -> toEntry(app, storeId, entryPermissions(actor, app.getAppKey())))
            .toList();
    }

    private static AppGateDecision denied(AppGateAccessRequest request, AppGateDenyReason reason) {
        return AppGateDecision.deny(request.appKey(), request.tenantId(), request.storeId(), request.requiredPermission(), reason);
    }

    private static AppGateDenyReason storeDenial(String appKey,StoreAppSubscription subscription,OffsetDateTime now) {
        // Wallet has no legacy tenant-wide grant: every trading store must be explicitly purchased.
        if(subscription==null) return "wallet".equals(appKey)?AppGateDenyReason.STORE_APP_NOT_ENABLED:null;
        return subscription.denialAt(now);
    }

    private static boolean isEnabledOrValidTrial(TenantAppEntitlementEntity entitlement) {
        return ENABLED.equals(entitlement.getStatus()) || TRIAL.equals(entitlement.getStatus());
    }

    private static boolean isExpired(TenantAppEntitlementEntity entitlement, OffsetDateTime now) {
        return "expired".equals(entitlement.getStatus())
            || (entitlement.getValidUntil() != null && !entitlement.getValidUntil().isAfter(now));
    }

    private static boolean isTenantAppVisible(TenantAppEntitlementEntity entitlement, OffsetDateTime now) {
        return entitlement != null && isEnabledOrValidTrial(entitlement) && !isExpired(entitlement, now);
    }

    private static boolean isStoreAppVisible(StoreAppSettingEntity setting) {
        return setting != null && setting.isEnabled() && setting.isEntryVisible();
    }

    private static boolean hasAnyEntryPermission(CurrentActor actor, String appKey) {
        return !entryPermissions(actor, appKey).isEmpty();
    }

    private static Set<String> entryPermissions(CurrentActor actor, String appKey) {
        return ProductLineEntryRegistry.permissions(appKey).stream().filter(actor::hasPermission)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static AppGateAppEntry toEntry(PlatformAppEntity app, UUID storeId, Set<String> permissions) {
        return new AppGateAppEntry(
            app.getAppKey(),
            app.getAppName(),
            app.getStatus(),
            app.getDefaultEntryRoute().replace(":storeId", storeId.toString()),
            true,
            permissions
        );
    }
}
