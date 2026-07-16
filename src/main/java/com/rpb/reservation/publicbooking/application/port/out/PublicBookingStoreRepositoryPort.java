package com.rpb.reservation.publicbooking.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.publicbooking.application.PublicBookingStoreProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicBookingStoreRepositoryPort {

    Optional<PublicBookingStoreProfile> findActiveStoreProfileByStoreId(UUID storeId);

    Optional<PublicBookingStoreProfile> findActiveStoreProfile(StoreScope scope);

    List<PublicBookingStoreProfile> findEnabledPublicBookingStoreProfilesByTenantCode(String tenantCode);
}
