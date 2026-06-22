package com.rpb.reservation.store.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.store.domain.StorePolicy;
import java.util.Optional;

public interface StorePolicyRepositoryPort {

    Optional<StorePolicy> findByStoreScope(StoreScope scope);
}
