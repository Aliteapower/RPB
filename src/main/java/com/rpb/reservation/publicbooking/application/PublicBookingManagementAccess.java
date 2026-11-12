package com.rpb.reservation.publicbooking.application;

import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiErrorCode;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiException;
import org.springframework.stereotype.Component;

/** Commercial boundary for the admin booking workspace, after identity/scope resolution. */
@Component
public class PublicBookingManagementAccess {
    private final AppGateService appGate;
    private final PublicBookingStoreRepositoryPort stores;

    public PublicBookingManagementAccess(AppGateService appGate, PublicBookingStoreRepositoryPort stores) {
        this.appGate = appGate;
        this.stores = stores;
    }

    public void require(StoreScope scope) {
        if (stores.findActiveStoreProfile(scope).isEmpty()) {
            throw new TenantAdminApiException(TenantAdminApiErrorCode.STORE_SCOPE_MISMATCH);
        }
        if (!appGate.evaluateCommercial("reservation_queue", scope.tenantId().value(), scope.storeId().value()).allowed()) {
            throw new TenantAdminApiException(TenantAdminApiErrorCode.FORBIDDEN);
        }
    }
}
