package com.rpb.reservation.store.persistence.adapter;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.store.application.port.out.StorePolicyRepositoryPort;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.store.persistence.mapper.StoreMapper;
import com.rpb.reservation.store.persistence.mapper.StorePolicyMapper;
import com.rpb.reservation.store.persistence.repository.StoreJpaRepository;
import com.rpb.reservation.store.persistence.repository.StorePolicyJpaRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class StorePersistenceAdapter implements StoreRepositoryPort, StorePolicyRepositoryPort {

    private final StoreJpaRepository storeRepository;
    private final StorePolicyJpaRepository policyRepository;
    private final StoreMapper storeMapper;
    private final StorePolicyMapper policyMapper;

    public StorePersistenceAdapter(
        StoreJpaRepository storeRepository,
        StorePolicyJpaRepository policyRepository,
        StoreMapper storeMapper,
        StorePolicyMapper policyMapper
    ) {
        this.storeRepository = storeRepository;
        this.policyRepository = policyRepository;
        this.storeMapper = storeMapper;
        this.policyMapper = policyMapper;
    }

    @Override
    public Optional<Store> findById(StoreScope scope) {
        return storeRepository.findByIdAndTenantIdAndDeletedAtIsNull(
            scope.storeId().value(),
            scope.tenantId().value()
        ).map(storeMapper::toDomain);
    }

    @Override
    public Optional<StorePolicy> findCurrentPolicy(StoreScope scope, OffsetDateTime at) {
        return policyRepository.findCurrentPolicy(
            scope.tenantId().value(),
            scope.storeId().value(),
            at
        ).map(policyMapper::toDomain);
    }

    @Override
    public Optional<StorePolicy> findByStoreScope(StoreScope scope) {
        return findCurrentPolicy(scope, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public Store save(StoreScope scope, Store store) {
        return storeMapper.toDomain(storeRepository.save(storeMapper.toEntity(store)));
    }

    @Override
    public StorePolicy savePolicy(StoreScope scope, StorePolicy policy) {
        return policyMapper.toDomain(policyRepository.save(policyMapper.toEntity(policy)));
    }
}
