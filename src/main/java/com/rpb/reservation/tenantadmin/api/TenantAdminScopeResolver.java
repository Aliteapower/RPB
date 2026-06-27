package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TenantAdminScopeResolver {
    private static final String TENANT_ADMIN = "tenant_admin";
    private static final String TENANT_ADMIN_MANAGE = "tenant.admin.manage";

    private final CurrentActorProvider currentActorProvider;

    public TenantAdminScopeResolver(CurrentActorProvider currentActorProvider) {
        this.currentActorProvider = currentActorProvider;
    }

    public StoreScope requireTenantAdminScope(UUID storeId) {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new TenantAdminApiException(TenantAdminApiErrorCode.UNAUTHENTICATED));
        if (!actor.roles().contains(TENANT_ADMIN) || !actor.hasPermission(TENANT_ADMIN_MANAGE)) {
            throw new TenantAdminApiException(TenantAdminApiErrorCode.FORBIDDEN);
        }
        if (actor.tenantId() == null || !actor.storeIds().contains(storeId)) {
            throw new TenantAdminApiException(TenantAdminApiErrorCode.STORE_SCOPE_MISMATCH);
        }
        return new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId));
    }
}
