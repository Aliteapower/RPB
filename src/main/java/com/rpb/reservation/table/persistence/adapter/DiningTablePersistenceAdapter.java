package com.rpb.reservation.table.persistence.adapter;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.table.application.DiningTableResourceRow;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.persistence.entity.DiningTableEntity;
import com.rpb.reservation.table.persistence.mapper.DiningTableMapper;
import com.rpb.reservation.table.persistence.repository.DiningTableJpaRepository;
import com.rpb.reservation.table.persistence.repository.DiningTableResourceProjection;
import com.rpb.reservation.table.value.TableId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class DiningTablePersistenceAdapter implements DiningTableRepositoryPort {

    private final DiningTableJpaRepository repository;
    private final DiningTableMapper mapper;

    public DiningTablePersistenceAdapter(DiningTableJpaRepository repository, DiningTableMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<DiningTable> findById(StoreScope scope, TableId tableId) {
        return repository.findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
            tableId.value(),
            scope.tenantId().value(),
            scope.storeId().value()
        ).map(mapper::toDomain);
    }

    @Override
    public List<DiningTable> findActiveByArea(StoreScope scope, UUID areaId) {
        return repository.findByTenantIdAndStoreIdAndAreaIdAndDeletedAtIsNull(
            scope.tenantId().value(),
            scope.storeId().value(),
            areaId
        ).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<DiningTable> findCandidates(StoreScope scope, PartySize partySize, BusinessDate businessDate) {
        return repository.findAvailableCandidates(
            scope.tenantId().value(),
            scope.storeId().value(),
            partySize.value()
        ).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<DiningTable> findVisibleResources(StoreScope scope, String status, PartySize partySize) {
        UUID tenantId = scope.tenantId().value();
        UUID storeId = scope.storeId().value();
        List<DiningTableEntity> entities;

        if (status != null && partySize != null) {
            entities = repository.findVisibleResourcesByStatusAndPartySize(
                tenantId,
                storeId,
                status,
                partySize.value()
            );
        } else if (status != null) {
            entities = repository.findVisibleResourcesByStatus(tenantId, storeId, status);
        } else if (partySize != null) {
            entities = repository.findVisibleResourcesByPartySize(tenantId, storeId, partySize.value());
        } else {
            entities = repository.findVisibleResourcesWithoutFilters(tenantId, storeId);
        }

        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<DiningTableResourceRow> findVisibleResourceRows(StoreScope scope, String status, PartySize partySize) {
        UUID tenantId = scope.tenantId().value();
        UUID storeId = scope.storeId().value();
        List<DiningTableResourceProjection> rows;

        if (status != null && partySize != null) {
            rows = repository.findVisibleResourceRowsByStatusAndPartySize(
                tenantId,
                storeId,
                status,
                partySize.value()
            );
        } else if (status != null) {
            rows = repository.findVisibleResourceRowsByStatus(tenantId, storeId, status);
        } else if (partySize != null) {
            rows = repository.findVisibleResourceRowsByPartySize(tenantId, storeId, partySize.value());
        } else {
            rows = repository.findVisibleResourceRowsWithoutFilters(tenantId, storeId);
        }

        return rows.stream().map(DiningTablePersistenceAdapter::toResourceRow).toList();
    }

    @Override
    public DiningTable save(StoreScope scope, DiningTable table) {
        DiningTableEntity mapped = mapper.toEntity(table);
        DiningTableEntity entity = repository.findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
            table.id().value(),
            scope.tenantId().value(),
            scope.storeId().value()
        ).map(existing -> existingEntity(mapped, existing))
            .orElseGet(() -> newEntity(mapped));
        return mapper.toDomain(repository.save(entity));
    }

    private static DiningTableEntity existingEntity(DiningTableEntity mapped, DiningTableEntity existing) {
        return DiningTableEntity.of(
            mapped.getId(),
            mapped.getTenantId(),
            mapped.getStoreId(),
            mapped.getAreaId(),
            mapped.getTableCode(),
            mapped.getDisplayName(),
            mapped.getCapacityMin(),
            mapped.getCapacityMax(),
            mapped.getStatus(),
            mapped.getCombinable(),
            existing.getCreatedAt(),
            mapped.getUpdatedAt(),
            existing.getDeletedAt(),
            existing.getVersion()
        );
    }

    private static DiningTableEntity newEntity(DiningTableEntity mapped) {
        return DiningTableEntity.of(
            mapped.getId(),
            mapped.getTenantId(),
            mapped.getStoreId(),
            mapped.getAreaId(),
            mapped.getTableCode(),
            mapped.getDisplayName(),
            mapped.getCapacityMin(),
            mapped.getCapacityMax(),
            mapped.getStatus(),
            mapped.getCombinable(),
            mapped.getCreatedAt(),
            mapped.getUpdatedAt(),
            mapped.getDeletedAt(),
            null
        );
    }

    private static DiningTableResourceRow toResourceRow(DiningTableResourceProjection projection) {
        return new DiningTableResourceRow(
            projection.getResourceId(),
            projection.getCode(),
            projection.getDisplayName(),
            projection.getAreaName(),
            projection.getCapacityMin(),
            projection.getCapacityMax(),
            projection.getStatus()
        );
    }
}
