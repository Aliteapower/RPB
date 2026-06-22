package com.rpb.reservation.table.persistence.adapter;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.table.application.port.out.TableLockRepositoryPort;
import com.rpb.reservation.table.domain.TableLock;
import com.rpb.reservation.table.persistence.entity.TableLockEntity;
import com.rpb.reservation.table.persistence.mapper.TableLockMapper;
import com.rpb.reservation.table.persistence.repository.TableLockJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class TableLockPersistenceAdapter implements TableLockRepositoryPort {

    private final TableLockJpaRepository repository;
    private final TableLockMapper mapper;
    private final EntityManager entityManager;

    public TableLockPersistenceAdapter(TableLockJpaRepository repository, TableLockMapper mapper, EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<TableLock> findActiveByResource(StoreScope scope, String resourceType, UUID resourceId) {
        return repository.findActiveByResource(
            scope.tenantId().value(),
            scope.storeId().value(),
            resourceType,
            resourceId
        ).map(mapper::toDomain);
    }

    @Override
    public boolean existsActiveConflict(StoreScope scope, String resourceType, UUID resourceId, OffsetDateTime at) {
        return repository.existsActiveConflict(
            scope.tenantId().value(),
            scope.storeId().value(),
            resourceType,
            resourceId,
            at
        );
    }

    @Override
    public TableLock save(StoreScope scope, TableLock lock) {
        TableLockEntity entity = mapper.toEntity(lock);
        if (repository.existsById(lock.id())) {
            return mapper.toDomain(repository.save(entity));
        }
        TableLockEntity newEntity = newEntity(entity);
        entityManager.persist(newEntity);
        return mapper.toDomain(newEntity);
    }

    @Override
    public TableLock release(StoreScope scope, UUID tableLockId, OffsetDateTime releasedAt) {
        TableLockEntity current = repository.findByIdAndTenantIdAndStoreId(
            tableLockId,
            scope.tenantId().value(),
            scope.storeId().value()
        ).orElseThrow(() -> new IllegalArgumentException("table_lock_not_found"));
        TableLockEntity released = TableLockEntity.of(
            current.getId(),
            current.getTenantId(),
            current.getStoreId(),
            current.getResourceType(),
            current.getResourceId(),
            current.getLockKey(),
            current.getLockOwner(),
            current.getLockedUntilAt(),
            current.getSourceType(),
            current.getSourceId(),
            current.getIdempotencyKey(),
            "released",
            current.getLockedAt(),
            releasedAt,
            current.getCreatedAt(),
            releasedAt,
            current.getVersion()
        );
        return mapper.toDomain(repository.save(released));
    }

    private static TableLockEntity newEntity(TableLockEntity entity) {
        return TableLockEntity.of(
            entity.getId(),
            entity.getTenantId(),
            entity.getStoreId(),
            entity.getResourceType(),
            entity.getResourceId(),
            entity.getLockKey(),
            entity.getLockOwner(),
            entity.getLockedUntilAt(),
            entity.getSourceType(),
            entity.getSourceId(),
            entity.getIdempotencyKey(),
            entity.getStatus(),
            entity.getLockedAt(),
            entity.getReleasedAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            null
        );
    }
}
