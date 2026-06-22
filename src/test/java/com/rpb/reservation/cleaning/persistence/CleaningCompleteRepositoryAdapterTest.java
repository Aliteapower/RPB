package com.rpb.reservation.cleaning.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rpb.reservation.cleaning.application.port.out.CleaningRepositoryPort;
import com.rpb.reservation.cleaning.domain.Cleaning;
import com.rpb.reservation.cleaning.persistence.adapter.CleaningPersistenceAdapter;
import com.rpb.reservation.cleaning.persistence.entity.CleaningEntity;
import com.rpb.reservation.cleaning.persistence.mapper.DefaultCleaningMapper;
import com.rpb.reservation.cleaning.persistence.repository.CleaningJpaRepository;
import com.rpb.reservation.cleaning.status.CleaningStatus;
import com.rpb.reservation.cleaning.value.CleaningId;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.persistence.adapter.IdempotencyPersistenceAdapter;
import com.rpb.reservation.idempotency.persistence.entity.IdempotencyRecordEntity;
import com.rpb.reservation.idempotency.persistence.mapper.DefaultIdempotencyMapper;
import com.rpb.reservation.idempotency.persistence.repository.IdempotencyRecordJpaRepository;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort;
import com.rpb.reservation.seating.domain.SeatingResource;
import com.rpb.reservation.seating.persistence.adapter.SeatingPersistenceAdapter;
import com.rpb.reservation.seating.persistence.entity.SeatingResourceEntity;
import com.rpb.reservation.seating.persistence.mapper.DefaultSeatingMapper;
import com.rpb.reservation.seating.persistence.mapper.DefaultSeatingResourceMapper;
import com.rpb.reservation.seating.persistence.repository.SeatingJpaRepository;
import com.rpb.reservation.seating.persistence.repository.SeatingResourceJpaRepository;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.persistence.adapter.DiningTablePersistenceAdapter;
import com.rpb.reservation.table.persistence.entity.DiningTableEntity;
import com.rpb.reservation.table.persistence.mapper.DefaultDiningTableMapper;
import com.rpb.reservation.table.persistence.repository.DiningTableJpaRepository;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CleaningCompleteRepositoryAdapterTest {

    private final TenantId tenantId = new TenantId(UUID.randomUUID());
    private final StoreId storeId = new StoreId(UUID.randomUUID());
    private final StoreScope scope = new StoreScope(tenantId, storeId);

    @Test
    void cleaningAdapterFindsByScopedCleaningId() {
        CleaningJpaRepository jpaRepository = mock(CleaningJpaRepository.class);
        CleaningId cleaningId = new CleaningId(UUID.randomUUID());
        UUID tableId = UUID.randomUUID();
        CleaningEntity entity = cleaningEntity(cleaningId.value(), UUID.randomUUID(), "dining_table", tableId, null, "cleaning");
        when(jpaRepository.findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
            cleaningId.value(),
            tenantId.value(),
            storeId.value()
        )).thenReturn(Optional.of(entity));

        CleaningRepositoryPort adapter = new CleaningPersistenceAdapter(
            jpaRepository,
            new DefaultCleaningMapper(),
            mock(EntityManager.class)
        );

        Optional<Cleaning> found = adapter.findById(scope, cleaningId);

        assertThat(found).isPresent();
        assertThat(found.get().scope()).isEqualTo(scope);
        assertThat(found.get().resourceId()).isEqualTo(tableId);
        assertThat(found.get().status()).isEqualTo(CleaningStatus.CLEANING);
        verify(jpaRepository).findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
            cleaningId.value(),
            tenantId.value(),
            storeId.value()
        );
    }

    @Test
    void cleaningAdapterFindsActiveCleaningByResourceWithinStoreScope() {
        CleaningJpaRepository jpaRepository = mock(CleaningJpaRepository.class);
        UUID tableId = UUID.randomUUID();
        CleaningEntity entity = cleaningEntity(UUID.randomUUID(), UUID.randomUUID(), "dining_table", tableId, null, "pending");
        when(jpaRepository.findActiveByResource(
            tenantId.value(),
            storeId.value(),
            "dining_table",
            tableId
        )).thenReturn(Optional.of(entity));

        CleaningRepositoryPort adapter = new CleaningPersistenceAdapter(
            jpaRepository,
            new DefaultCleaningMapper(),
            mock(EntityManager.class)
        );

        Optional<Cleaning> active = adapter.findActiveByResource(scope, "dining_table", tableId);

        assertThat(active).isPresent();
        assertThat(active.get().status()).isEqualTo(CleaningStatus.PENDING);
        verify(jpaRepository).findActiveByResource(tenantId.value(), storeId.value(), "dining_table", tableId);
    }

    @Test
    void cleaningAdapterFindsLatestCleaningBySeatingWithinStoreScope() {
        CleaningJpaRepository jpaRepository = mock(CleaningJpaRepository.class);
        SeatingId seatingId = new SeatingId(UUID.randomUUID());
        UUID tableGroupId = UUID.randomUUID();
        CleaningEntity entity = cleaningEntity(UUID.randomUUID(), seatingId.value(), "table_group", null, tableGroupId, "released");
        when(jpaRepository.findFirstByTenantIdAndStoreIdAndSeatingIdAndDeletedAtIsNullOrderByStartedAtDesc(
            tenantId.value(),
            storeId.value(),
            seatingId.value()
        )).thenReturn(Optional.of(entity));

        CleaningRepositoryPort adapter = new CleaningPersistenceAdapter(
            jpaRepository,
            new DefaultCleaningMapper(),
            mock(EntityManager.class)
        );

        Optional<Cleaning> found = adapter.findBySeating(scope, seatingId);

        assertThat(found).isPresent();
        assertThat(found.get().resourceType()).isEqualTo("table_group");
        assertThat(found.get().resourceId()).isEqualTo(tableGroupId);
        verify(jpaRepository).findFirstByTenantIdAndStoreIdAndSeatingIdAndDeletedAtIsNullOrderByStartedAtDesc(
            tenantId.value(),
            storeId.value(),
            seatingId.value()
        );
    }

    @Test
    void seatingAdapterFindsActiveResourceBySeatingForCleaningResourceDerivation() {
        SeatingJpaRepository seatingRepository = mock(SeatingJpaRepository.class);
        SeatingResourceJpaRepository resourceRepository = mock(SeatingResourceJpaRepository.class);
        SeatingId seatingId = new SeatingId(UUID.randomUUID());
        UUID tableId = UUID.randomUUID();
        SeatingResourceEntity entity = SeatingResourceEntity.of(
            UUID.randomUUID(),
            tenantId.value(),
            storeId.value(),
            seatingId.value(),
            "dining_table",
            tableId,
            null,
            OffsetDateTime.now(),
            null,
            "active",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            null
        );
        when(resourceRepository.findFirstByTenantIdAndStoreIdAndSeatingIdAndStatusAndDeletedAtIsNullOrderByAssignedAtDesc(
            tenantId.value(),
            storeId.value(),
            seatingId.value(),
            "active"
        )).thenReturn(Optional.of(entity));

        SeatingRepositoryPort adapter = new SeatingPersistenceAdapter(
            seatingRepository,
            resourceRepository,
            new DefaultSeatingMapper(),
            new DefaultSeatingResourceMapper(),
            mock(EntityManager.class)
        );

        Optional<SeatingResource> resource = adapter.findActiveResourceBySeating(scope, seatingId);

        assertThat(resource).isPresent();
        assertThat(resource.get().resourceType()).isEqualTo("dining_table");
        assertThat(resource.get().resourceId()).isEqualTo(tableId);
        verify(resourceRepository).findFirstByTenantIdAndStoreIdAndSeatingIdAndStatusAndDeletedAtIsNullOrderByAssignedAtDesc(
            tenantId.value(),
            storeId.value(),
            seatingId.value(),
            "active"
        );
    }

    @Test
    void diningTableAdapterPersistsCleaningAndAvailableStatusesWithinScope() {
        DiningTableJpaRepository jpaRepository = mock(DiningTableJpaRepository.class);
        when(jpaRepository.save(any(DiningTableEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DiningTableRepositoryPort adapter = new DiningTablePersistenceAdapter(jpaRepository, new DefaultDiningTableMapper());
        TableId tableId = new TableId(UUID.randomUUID());
        UUID areaId = UUID.randomUUID();

        adapter.save(scope, new DiningTable(
            tableId,
            scope,
            areaId,
            "A1",
            new CapacityRange(1, 4),
            DiningTableStatus.CLEANING,
            true
        ));
        adapter.save(scope, new DiningTable(
            tableId,
            scope,
            areaId,
            "A1",
            new CapacityRange(1, 4),
            DiningTableStatus.AVAILABLE,
            true
        ));

        ArgumentCaptor<DiningTableEntity> captor = ArgumentCaptor.forClass(DiningTableEntity.class);
        verify(jpaRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo("cleaning");
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("available");
    }

    @Test
    void idempotencyAdapterSupportsCleaningActionKeys() {
        IdempotencyRecordJpaRepository jpaRepository = mock(IdempotencyRecordJpaRepository.class);
        when(jpaRepository.save(any(IdempotencyRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        IdempotencyRepositoryPort adapter = new IdempotencyPersistenceAdapter(jpaRepository, new DefaultIdempotencyMapper());

        IdempotencyRecord started = adapter.start(
            scope,
            "staff",
            "start_cleaning",
            new IdempotencyKey("cleaning-start-1"),
            "hash-start-cleaning",
            OffsetDateTime.now().plusMinutes(15)
        );

        assertThat(started.status()).isEqualTo(IdempotencyStatus.STARTED);
        assertThat(started.action()).isEqualTo("start_cleaning");
        assertThat(started.requestHash()).isEqualTo("hash-start-cleaning");
    }

    private CleaningEntity cleaningEntity(
        UUID cleaningId,
        UUID seatingId,
        String resourceType,
        UUID tableId,
        UUID tableGroupId,
        String status
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        return CleaningEntity.of(
            cleaningId,
            tenantId.value(),
            storeId.value(),
            seatingId,
            resourceType,
            tableId,
            tableGroupId,
            status,
            now,
            status.equals("completed") || status.equals("released") ? now : null,
            status.equals("released") ? now : null,
            null,
            now,
            now,
            null,
            0
        );
    }
}
