package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiErrorCode;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;

/** Limits membership changes to the administrator's stores while preserving other store assignments. */
public final class StaffStoreAccessPolicy {
    private StaffStoreAccessPolicy() {}

    public static void requireActor(StoreScope scope, CurrentActor actor) {
        if (actor == null || actor.actorId() == null || !scope.tenantId().value().equals(actor.tenantId())
            || !actor.canAccessStore(scope.storeId().value()) || !actor.roles().contains("tenant_admin")
            || !actor.hasPermission("tenant.admin.manage") || "platform_admin".equals(actor.actorType())) deny();
    }
    public static void requireCreate(StoreScope scope, CurrentActor actor, List<UUID> stores, UUID defaultStore) {
        requireActor(scope, actor);
        if (!actor.storeIds().containsAll(stores) || !actor.canAccessStore(defaultStore)) deny();
    }
    public static void requireUpdate(StoreScope scope, CurrentActor actor, List<UUID> existingStores, UUID existingDefault,
        List<UUID> requestedStores, UUID requestedDefault) {
        requireActor(scope, actor);
        if (!outside(existingStores, actor.storeIds()).equals(outside(requestedStores, actor.storeIds()))) deny();
        if (!Objects.equals(existingDefault, requestedDefault) && !actor.canAccessStore(requestedDefault)) deny();
    }
    private static Set<UUID> outside(List<UUID> stores, Set<UUID> allowed) {
        var outside = new HashSet<>(stores); outside.removeAll(allowed); return outside;
    }
    private static void deny() { throw new TenantAdminApiException(TenantAdminApiErrorCode.FORBIDDEN); }
}
