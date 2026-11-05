package com.rpb.reservation.publicbooking.application;

import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicBookingEntryApplicationService {
    private final PublicBookingStoreRepositoryPort storeRepository;
    private final AppGateService appGate;

    public PublicBookingEntryApplicationService(PublicBookingStoreRepositoryPort storeRepository, AppGateService appGate) {
        this.storeRepository = storeRepository;
        this.appGate = appGate;
    }

    @Transactional(readOnly = true)
    public PublicBookingEntryResult resolveTenantEntry(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return PublicBookingEntryResult.failure(PublicBookingEntryError.TENANT_CONTEXT_REQUIRED);
        }
        List<PublicBookingStoreProfile> stores = storeRepository.findEnabledPublicBookingStoreProfilesByTenantCode(
            tenantCode.trim()
        ).stream().filter(store -> appGate.evaluateCommercial("reservation_queue",
            store.scope().tenantId().value(), store.scope().storeId().value()).allowed()).toList();
        if (stores.isEmpty()) {
            return PublicBookingEntryResult.failure(PublicBookingEntryError.STORE_NOT_FOUND);
        }
        if (stores.size() > 1) {
            return PublicBookingEntryResult.failure(PublicBookingEntryError.MULTIPLE_ENABLED_STORES);
        }
        return PublicBookingEntryResult.success(stores.get(0));
    }
}
