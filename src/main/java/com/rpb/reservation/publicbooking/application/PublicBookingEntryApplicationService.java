package com.rpb.reservation.publicbooking.application;

import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicBookingEntryApplicationService {
    private final PublicBookingStoreRepositoryPort storeRepository;

    public PublicBookingEntryApplicationService(PublicBookingStoreRepositoryPort storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Transactional(readOnly = true)
    public PublicBookingEntryResult resolveTenantEntry(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return PublicBookingEntryResult.failure(PublicBookingEntryError.TENANT_CONTEXT_REQUIRED);
        }
        List<PublicBookingStoreProfile> stores = storeRepository.findEnabledPublicBookingStoreProfilesByTenantCode(
            tenantCode.trim()
        );
        if (stores.isEmpty()) {
            return PublicBookingEntryResult.failure(PublicBookingEntryError.STORE_NOT_FOUND);
        }
        if (stores.size() > 1) {
            return PublicBookingEntryResult.failure(PublicBookingEntryError.MULTIPLE_ENABLED_STORES);
        }
        return PublicBookingEntryResult.success(stores.get(0));
    }
}
