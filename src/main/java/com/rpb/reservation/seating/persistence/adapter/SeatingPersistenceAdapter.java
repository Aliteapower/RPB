package com.rpb.reservation.seating.persistence.adapter;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort;
import com.rpb.reservation.seating.domain.Seating;
import com.rpb.reservation.seating.domain.SeatingResource;
import com.rpb.reservation.seating.persistence.entity.SeatingEntity;
import com.rpb.reservation.seating.persistence.mapper.SeatingMapper;
import com.rpb.reservation.seating.persistence.mapper.SeatingResourceMapper;
import com.rpb.reservation.seating.persistence.repository.SeatingJpaRepository;
import com.rpb.reservation.seating.persistence.repository.SeatingResourceJpaRepository;
import com.rpb.reservation.seating.value.SeatingId;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class SeatingPersistenceAdapter implements SeatingRepositoryPort {

    private final SeatingJpaRepository seatingRepository;
    private final SeatingResourceJpaRepository resourceRepository;
    private final SeatingMapper seatingMapper;
    private final SeatingResourceMapper resourceMapper;
    private final EntityManager entityManager;

    public SeatingPersistenceAdapter(
        SeatingJpaRepository seatingRepository,
        SeatingResourceJpaRepository resourceRepository,
        SeatingMapper seatingMapper,
        SeatingResourceMapper resourceMapper,
        EntityManager entityManager
    ) {
        this.seatingRepository = seatingRepository;
        this.resourceRepository = resourceRepository;
        this.seatingMapper = seatingMapper;
        this.resourceMapper = resourceMapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Seating> findById(StoreScope scope, SeatingId seatingId) {
        return seatingRepository.findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
            seatingId.value(),
            scope.tenantId().value(),
            scope.storeId().value()
        ).map(seatingMapper::toDomain);
    }

    @Override
    public Optional<Seating> findActiveBySource(StoreScope scope, String sourceType, UUID sourceId) {
        return seatingRepository.findActiveBySource(
            scope.tenantId().value(),
            scope.storeId().value(),
            sourceType,
            sourceId
        ).map(seatingMapper::toDomain);
    }

    @Override
    public boolean existsActiveResourceOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
        return resourceRepository.existsActiveResourceOccupancy(
            scope.tenantId().value(),
            scope.storeId().value(),
            resourceType,
            resourceId
        );
    }

    @Override
    public Optional<Seating> findActiveOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
        return resourceRepository.findActiveOccupancyResource(
            scope.tenantId().value(),
            scope.storeId().value(),
            resourceType,
            resourceId
        ).flatMap(resource -> findById(scope, new SeatingId(resource.getSeatingId())));
    }

    @Override
    public Optional<SeatingResource> findActiveResourceBySeating(StoreScope scope, SeatingId seatingId) {
        return resourceRepository.findFirstByTenantIdAndStoreIdAndSeatingIdAndStatusAndDeletedAtIsNullOrderByAssignedAtDesc(
            scope.tenantId().value(),
            scope.storeId().value(),
            seatingId.value(),
            "active"
        ).map(resourceMapper::toDomain);
    }

    @Override
    public Seating save(StoreScope scope, Seating seating) {
        SeatingEntity entity = seatingMapper.toEntity(seating);
        if (seatingRepository.existsById(seating.id().value())) {
            return seatingMapper.toDomain(seatingRepository.save(entity));
        }
        SeatingEntity newEntity = newEntity(entity);
        entityManager.persist(newEntity);
        return seatingMapper.toDomain(newEntity);
    }

    @Override
    public SeatingResource saveResource(StoreScope scope, SeatingResource resource) {
        return resourceMapper.toDomain(resourceRepository.save(resourceMapper.toEntity(resource)));
    }

    private static SeatingEntity newEntity(SeatingEntity entity) {
        return SeatingEntity.of(
            entity.getId(),
            entity.getTenantId(),
            entity.getStoreId(),
            entity.getReservationId(),
            entity.getQueueTicketId(),
            entity.getWalkInId(),
            entity.getSeatingCode(),
            entity.getPartySizeSnapshot(),
            entity.getStatus(),
            entity.getSeatedAt(),
            entity.getCompletedAt(),
            entity.getManualOverrideReasonCode(),
            entity.getNote(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getDeletedAt(),
            null
        );
    }
}
