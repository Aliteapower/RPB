package com.rpb.reservation.cleaning.persistence.adapter;

import com.rpb.reservation.cleaning.application.port.out.CleaningRepositoryPort;
import com.rpb.reservation.cleaning.domain.Cleaning;
import com.rpb.reservation.cleaning.persistence.entity.CleaningEntity;
import com.rpb.reservation.cleaning.persistence.mapper.CleaningMapper;
import com.rpb.reservation.cleaning.persistence.repository.CleaningJpaRepository;
import com.rpb.reservation.cleaning.value.CleaningId;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.seating.value.SeatingId;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CleaningPersistenceAdapter implements CleaningRepositoryPort {

    private final CleaningJpaRepository repository;
    private final CleaningMapper mapper;
    private final EntityManager entityManager;

    public CleaningPersistenceAdapter(CleaningJpaRepository repository, CleaningMapper mapper, EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Cleaning> findById(StoreScope scope, CleaningId cleaningId) {
        return repository.findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
            cleaningId.value(),
            scope.tenantId().value(),
            scope.storeId().value()
        ).map(mapper::toDomain);
    }

    @Override
    public Optional<Cleaning> findActiveByResource(StoreScope scope, String resourceType, UUID resourceId) {
        return repository.findActiveByResource(
            scope.tenantId().value(),
            scope.storeId().value(),
            resourceType,
            resourceId
        ).map(mapper::toDomain);
    }

    @Override
    public Optional<Cleaning> findBySeating(StoreScope scope, SeatingId seatingId) {
        return repository.findFirstByTenantIdAndStoreIdAndSeatingIdAndDeletedAtIsNullOrderByStartedAtDesc(
            scope.tenantId().value(),
            scope.storeId().value(),
            seatingId.value()
        ).map(mapper::toDomain);
    }

    @Override
    public Cleaning save(StoreScope scope, Cleaning cleaning) {
        CleaningEntity entity = mapper.toEntity(cleaning);
        if (repository.existsById(cleaning.id().value())) {
            return mapper.toDomain(repository.save(entity));
        }
        CleaningEntity newEntity = newEntity(entity);
        entityManager.persist(newEntity);
        return mapper.toDomain(newEntity);
    }

    private static CleaningEntity newEntity(CleaningEntity entity) {
        return CleaningEntity.of(
            entity.getId(),
            entity.getTenantId(),
            entity.getStoreId(),
            entity.getSeatingId(),
            entity.getResourceType(),
            entity.getTableId(),
            entity.getTableGroupId(),
            entity.getStatus(),
            entity.getStartedAt(),
            entity.getCompletedAt(),
            entity.getReleasedAt(),
            entity.getNote(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getDeletedAt(),
            null
        );
    }
}
