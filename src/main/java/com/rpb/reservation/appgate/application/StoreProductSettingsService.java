package com.rpb.reservation.appgate.application;

import com.rpb.reservation.appgate.persistence.repository.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.appgate.application.StoreProductSettingsException.Reason.*;

@Service
@Transactional(readOnly = true)
public class StoreProductSettingsService {
    private static final List<String> PRODUCTS = List.of("wallet", "daily_ledger", "wine_storage");
    private final StoreProductManagementRepository stores;
    private final StoreAppSettingJpaRepository settings;
    private final AppGateCommandService commands;
    private final AppGateService gate;
    public StoreProductSettingsService(StoreProductManagementRepository stores, StoreAppSettingJpaRepository settings,
        AppGateCommandService commands, AppGateService gate) {
        this.stores=stores; this.settings=settings; this.commands=commands; this.gate=gate;
    }
    public List<StoreProductSetting> list(StoreScope scope) {
        requireStore(scope, false);
        return PRODUCTS.stream().filter(key -> gate.isStoreProductProvisioned(key,scope.tenantId().value(),scope.storeId().value()))
            .map(key -> state(scope,key)).toList();
    }
    @Transactional
    public StoreProductSetting setEnabled(StoreScope scope, CurrentActor actor, String key, boolean enabled) {
        if (actor == null || actor.actorId() == null || !scope.tenantId().value().equals(actor.tenantId())
            || !actor.canAccessStore(scope.storeId().value()) || !actor.roles().contains("tenant_admin")
            || !actor.hasPermission("tenant.admin.manage")) throw new StoreProductSettingsException(FORBIDDEN);
        if (!PRODUCTS.contains(key)) throw new StoreProductSettingsException(INVALID_PRODUCT);
        requireStore(scope, true);
        var before=state(scope,key);
        if (enabled && !before.canEnable()) throw new StoreProductSettingsException(UNAVAILABLE);
        if (before.enabled() != enabled) {
            if (enabled) commands.enableStoreApp(scope.tenantId().value(),scope.storeId().value(),key,actor.actorId(),"tenant_admin");
            else commands.disableStoreApp(scope.tenantId().value(),scope.storeId().value(),key,actor.actorId(),"tenant_admin");
        }
        return new StoreProductSetting(key,enabled,before.canEnable(),before.unavailableReason());
    }
    private StoreProductSetting state(StoreScope scope,String key) {
        var activation=gate.evaluateStoreActivation(key,scope.tenantId().value(),scope.storeId().value());
        boolean enabled=settings.findByTenantIdAndStoreIdAndAppKey(scope.tenantId().value(),scope.storeId().value(),key)
            .map(setting -> setting.isEnabled()).orElse(false);
        return new StoreProductSetting(key,enabled,activation.allowed(),activation.allowed()?null:activation.denyReason().name());
    }
    private void requireStore(StoreScope scope,boolean lock) {
        if (!stores.isActive(scope,lock)) throw new StoreProductSettingsException(STORE_NOT_FOUND);
    }
}
