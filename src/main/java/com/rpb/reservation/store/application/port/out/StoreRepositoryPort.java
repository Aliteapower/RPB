package com.rpb.reservation.store.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.domain.StorePolicy;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface StoreRepositoryPort {

    Optional<Store> findById(StoreScope scope);

    default Optional<Store> findByScope(StoreScope scope) {
        return findById(scope);
    }

    Optional<StorePolicy> findCurrentPolicy(StoreScope scope, OffsetDateTime at);

    Store save(StoreScope scope, Store store);

    StorePolicy savePolicy(StoreScope scope, StorePolicy policy);
}
