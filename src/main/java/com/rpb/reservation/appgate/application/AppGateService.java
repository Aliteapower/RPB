package com.rpb.reservation.appgate.application;

import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.appgate.domain.AppGateDenyReason;
import com.rpb.reservation.appgate.domain.AppGateRequiredPermission;
import com.rpb.reservation.appgate.persistence.entity.PlatformAppEntity;
import com.rpb.reservation.appgate.persistence.entity.StoreAppSettingEntity;
import com.rpb.reservation.appgate.persistence.entity.TenantAppEntitlementEntity;
import com.rpb.reservation.appgate.persistence.repository.PlatformAppJpaRepository;
import com.rpb.reservation.appgate.persistence.repository.StoreAppSettingJpaRepository;
import com.rpb.reservation.appgate.persistence.repository.TenantAppEntitlementJpaRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.OffsetDateTime;
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

    public AppGateService(
        PlatformAppJpaRepository platformApps,
        TenantAppEntitlementJpaRepository entitlements,
        StoreAppSettingJpaRepository storeSettings
    ) {
        this.platformApps = platformApps;
        this.entitlements = entitlements;
        this.storeSettings = storeSettings;
    }

    public AppGateDecision evaluate(AppGateAccessRequest request) {
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
        if (isExpired(tenantApp)) {
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
        if (actor == null || !actor.canAccessStore(request.storeId())) {
            return denied(request, AppGateDenyReason.STORE_ACCESS_DENIED);
        }
        if (request.requiredPermission() != null && !request.requiredPermission().isBlank()
            && !actor.hasPermission(request.requiredPermission())) {
            return denied(request, AppGateDenyReason.PERMISSION_DENIED);
        }

        return AppGateDecision.allow(request.appKey(), request.tenantId(), request.storeId(), request.requiredPermission());
    }

    public List<AppGateAppEntry> visibleApps(CurrentActor actor, UUID storeId) {
        if (actor == null || !actor.canAccessStore(storeId)) {
            return List.of();
        }
        UUID tenantId = actor.tenantId();
        Map<String, TenantAppEntitlementEntity> tenantApps = entitlements.findAllByTenantId(tenantId).stream()
            .collect(Collectors.toMap(TenantAppEntitlementEntity::getAppKey, Function.identity(), (left, right) -> left));
        Map<String, StoreAppSettingEntity> storeApps = storeSettings.findAllByTenantIdAndStoreId(tenantId, storeId).stream()
            .collect(Collectors.toMap(StoreAppSettingEntity::getAppKey, Function.identity(), (left, right) -> left));

        return platformApps.findAllByStatusOrderBySortOrderAscAppKeyAsc(ACTIVE).stream()
            .filter(app -> isTenantAppVisible(tenantApps.get(app.getAppKey())))
            .filter(app -> isStoreAppVisible(storeApps.get(app.getAppKey())))
            .filter(app -> hasAnyEntryPermission(actor, app.getAppKey()))
            .sorted(Comparator.comparingInt(PlatformAppEntity::getSortOrder).thenComparing(PlatformAppEntity::getAppKey))
            .map(app -> toEntry(app, storeId, entryPermissions(actor, app.getAppKey())))
            .toList();
    }

    private static AppGateDecision denied(AppGateAccessRequest request, AppGateDenyReason reason) {
        return AppGateDecision.deny(request.appKey(), request.tenantId(), request.storeId(), request.requiredPermission(), reason);
    }

    private static boolean isEnabledOrValidTrial(TenantAppEntitlementEntity entitlement) {
        return ENABLED.equals(entitlement.getStatus()) || TRIAL.equals(entitlement.getStatus());
    }

    private static boolean isExpired(TenantAppEntitlementEntity entitlement) {
        return "expired".equals(entitlement.getStatus())
            || (entitlement.getValidUntil() != null && !entitlement.getValidUntil().isAfter(OffsetDateTime.now()));
    }

    private static boolean isTenantAppVisible(TenantAppEntitlementEntity entitlement) {
        return entitlement != null && isEnabledOrValidTrial(entitlement) && !isExpired(entitlement);
    }

    private static boolean isStoreAppVisible(StoreAppSettingEntity setting) {
        return setting != null && setting.isEnabled() && setting.isEntryVisible();
    }

    private static boolean hasAnyEntryPermission(CurrentActor actor, String appKey) {
        return !entryPermissions(actor, appKey).isEmpty();
    }

    private static Set<String> entryPermissions(CurrentActor actor, String appKey) {
        if (!"reservation_queue".equals(appKey)) {
            return Set.of();
        }
        return AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS.stream()
            .filter(actor::hasPermission)
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
