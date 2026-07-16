package com.rpb.reservation.walkin.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.persistence.adapter.IdempotencyPersistenceAdapter;
import com.rpb.reservation.idempotency.persistence.entity.IdempotencyRecordEntity;
import com.rpb.reservation.idempotency.persistence.mapper.DefaultIdempotencyMapper;
import com.rpb.reservation.idempotency.persistence.repository.IdempotencyRecordJpaRepository;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.persistence.adapter.DiningTablePersistenceAdapter;
import com.rpb.reservation.table.persistence.entity.DiningTableEntity;
import com.rpb.reservation.table.persistence.mapper.DefaultDiningTableMapper;
import com.rpb.reservation.table.persistence.repository.DiningTableJpaRepository;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WalkInDirectSeatingRepositoryAdapterTest {

    private final TenantId tenantId = new TenantId(UUID.randomUUID());
    private final StoreId storeId = new StoreId(UUID.randomUUID());
    private final StoreScope scope = new StoreScope(tenantId, storeId);

    @Test
    void diningTableAdapterFindsAvailableCandidatesWithinStoreScopeAndCapacity() {
        DiningTableJpaRepository jpaRepository = mock(DiningTableJpaRepository.class);
        DiningTableEntity tableEntity = DiningTableEntity.of(
            UUID.randomUUID(),
            tenantId.value(),
            storeId.value(),
            UUID.randomUUID(),
            "T-01",
            "Table 01",
            1,
            4,
            "available",
            0,
            true,
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            null,
            0
        );
        when(jpaRepository.findAvailableCandidates(tenantId.value(), storeId.value(), 4))
            .thenReturn(List.of(tableEntity));

        DiningTableRepositoryPort adapter = new DiningTablePersistenceAdapter(
            jpaRepository,
            new DefaultDiningTableMapper()
        );

        List<DiningTable> candidates = adapter.findCandidates(scope, new PartySize(4), null);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().scope()).isEqualTo(scope);
        assertThat(candidates.getFirst().status()).isEqualTo(DiningTableStatus.AVAILABLE);
        verify(jpaRepository).findAvailableCandidates(tenantId.value(), storeId.value(), 4);
    }

    @Test
    void idempotencyAdapterStartsStoreScopedRecordWithRequestHash() {
        IdempotencyRecordJpaRepository jpaRepository = mock(IdempotencyRecordJpaRepository.class);
        DefaultIdempotencyMapper mapper = new DefaultIdempotencyMapper();
        when(jpaRepository.save(org.mockito.ArgumentMatchers.any(IdempotencyRecordEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        IdempotencyRepositoryPort adapter = new IdempotencyPersistenceAdapter(jpaRepository, mapper);

        IdempotencyRecord started = adapter.start(
            scope,
            "staff",
            "seat_walk_in_directly",
            new IdempotencyKey("idem-2001"),
            "hash-2001",
            OffsetDateTime.now().plusMinutes(15)
        );

        assertThat(started.status()).isEqualTo(IdempotencyStatus.STARTED);
        assertThat(started.requestHash()).isEqualTo("hash-2001");

        ArgumentCaptor<IdempotencyRecordEntity> captor = ArgumentCaptor.forClass(IdempotencyRecordEntity.class);
        verify(jpaRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId.value());
        assertThat(captor.getValue().getStoreId()).isEqualTo(storeId.value());
        assertThat(captor.getValue().getRequestHash()).isEqualTo("hash-2001");
        assertThat(captor.getValue().getStatus()).isEqualTo("started");
    }

    @Test
    void idempotencyAdapterFindsExistingRecordByScopeSourceActionAndKey() {
        IdempotencyRecordJpaRepository jpaRepository = mock(IdempotencyRecordJpaRepository.class);
        IdempotencyRecordEntity existing = IdempotencyRecordEntity.of(
            UUID.randomUUID(),
            tenantId.value(),
            storeId.value(),
            "idem-2002",
            "staff",
            "seat_walk_in_directly",
            "seating",
            UUID.randomUUID(),
            "hash-2002",
            "{\"status\":\"completed\"}",
            "completed",
            OffsetDateTime.now().plusMinutes(15),
            OffsetDateTime.now(),
            OffsetDateTime.now()
        );
        when(jpaRepository.findByTenantIdAndStoreIdAndSourceAndActionAndIdempotencyKey(
            tenantId.value(),
            storeId.value(),
            "staff",
            "seat_walk_in_directly",
            "idem-2002"
        )).thenReturn(Optional.of(existing));

        IdempotencyRepositoryPort adapter = new IdempotencyPersistenceAdapter(
            jpaRepository,
            new DefaultIdempotencyMapper()
        );

        Optional<IdempotencyRecord> found = adapter.findByScopeActionKey(
            scope,
            "staff",
            "seat_walk_in_directly",
            new IdempotencyKey("idem-2002")
        );

        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(found.get().requestHash()).isEqualTo("hash-2002");
        verify(jpaRepository).findByTenantIdAndStoreIdAndSourceAndActionAndIdempotencyKey(
            eq(tenantId.value()),
            eq(storeId.value()),
            eq("staff"),
            eq("seat_walk_in_directly"),
            eq("idem-2002")
        );
    }
}
