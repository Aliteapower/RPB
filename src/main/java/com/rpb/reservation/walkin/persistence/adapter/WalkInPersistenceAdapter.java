package com.rpb.reservation.walkin.persistence.adapter;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.walkin.application.port.out.WalkInRepositoryPort;
import com.rpb.reservation.walkin.domain.WalkIn;
import com.rpb.reservation.walkin.persistence.entity.WalkInEntity;
import com.rpb.reservation.walkin.persistence.mapper.WalkInMapper;
import com.rpb.reservation.walkin.persistence.repository.WalkInJpaRepository;
import com.rpb.reservation.walkin.value.WalkInId;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class WalkInPersistenceAdapter implements WalkInRepositoryPort {

    private final WalkInJpaRepository repository;
    private final WalkInMapper mapper;
    private final EntityManager entityManager;

    public WalkInPersistenceAdapter(WalkInJpaRepository repository, WalkInMapper mapper, EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<WalkIn> findById(StoreScope scope, WalkInId walkInId) {
        return repository.findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
            walkInId.value(),
            scope.tenantId().value(),
            scope.storeId().value()
        ).map(mapper::toDomain);
    }

    @Override
    public Optional<WalkIn> findByCode(StoreScope scope, String walkInCode) {
        return repository.findByTenantIdAndStoreIdAndWalkInCodeAndDeletedAtIsNull(
            scope.tenantId().value(),
            scope.storeId().value(),
            walkInCode
        ).map(mapper::toDomain);
    }

    @Override
    public List<WalkIn> findArrivals(StoreScope scope, BusinessDate businessDate, String statusCode) {
        return repository.findByTenantIdAndStoreIdAndBusinessDateAndStatusAndDeletedAtIsNullOrderByArrivedAtAsc(
            scope.tenantId().value(),
            scope.storeId().value(),
            businessDate.value(),
            statusCode
        ).stream().map(mapper::toDomain).toList();
    }

    @Override
    public WalkIn save(StoreScope scope, WalkIn walkIn) {
        WalkInEntity entity = mapper.toEntity(walkIn);
        if (repository.existsById(walkIn.id().value())) {
            return mapper.toDomain(repository.save(entity));
        }
        WalkInEntity newEntity = newEntity(entity);
        entityManager.persist(newEntity);
        return mapper.toDomain(newEntity);
    }

    private static WalkInEntity newEntity(WalkInEntity entity) {
        return WalkInEntity.of(
            entity.getId(),
            entity.getTenantId(),
            entity.getStoreId(),
            entity.getCustomerId(),
            entity.getWalkInCode(),
            entity.getPartySize(),
            entity.getBusinessDate(),
            entity.getArrivedAt(),
            entity.getStatus(),
            entity.getNote(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getDeletedAt(),
            null
        );
    }
}
