package com.rpb.reservation.reservation.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceAssignment;
import com.rpb.reservation.reservation.persistence.adapter.ReservationPersistenceAdapter;
import com.rpb.reservation.reservation.persistence.adapter.ReservationPreassignmentPersistenceAdapter;
import com.rpb.reservation.reservation.persistence.entity.ReservationEntity;
import com.rpb.reservation.reservation.persistence.mapper.DefaultReservationMapper;
import com.rpb.reservation.reservation.persistence.repository.ReservationJpaRepository;
import com.rpb.reservation.reservation.persistence.repository.ReservationPreassignmentJpaRepository;
import com.rpb.reservation.reservation.persistence.repository.ReservationResourceAssignmentProjection;
import com.rpb.reservation.reservation.value.ReservationId;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.persistence.adapter.DiningTablePersistenceAdapter;
import com.rpb.reservation.table.persistence.entity.DiningTableEntity;
import com.rpb.reservation.table.persistence.mapper.DefaultDiningTableMapper;
import com.rpb.reservation.table.persistence.repository.DiningTableJpaRepository;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationTableAssignmentPersistenceAdapterTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final StoreScope SCOPE = new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));

    @Test
    void reservationLockLookupKeepsTenantAndStoreScope() {
        ReservationJpaRepository repository = mock(ReservationJpaRepository.class);
        UUID reservationId = UUID.randomUUID();
        when(repository.findForUpdate(reservationId, TENANT_ID, STORE_ID))
            .thenReturn(Optional.of(reservationEntity(reservationId)));
        ReservationPersistenceAdapter adapter = new ReservationPersistenceAdapter(
            repository,
            new DefaultReservationMapper(),
            mock(EntityManager.class)
        );

        assertThat(adapter.findByIdForUpdate(SCOPE, new ReservationId(reservationId))).isPresent();

        verify(repository).findForUpdate(reservationId, TENANT_ID, STORE_ID);
    }

    @Test
    void diningTableLockLookupKeepsTenantAndStoreScope() {
        DiningTableJpaRepository repository = mock(DiningTableJpaRepository.class);
        UUID tableId = UUID.randomUUID();
        when(repository.findForUpdate(tableId, TENANT_ID, STORE_ID))
            .thenReturn(Optional.of(tableEntity(tableId)));
        DiningTablePersistenceAdapter adapter = new DiningTablePersistenceAdapter(
            repository,
            new DefaultDiningTableMapper()
        );

        assertThat(adapter.findByIdForUpdate(SCOPE, new TableId(tableId))).isPresent();

        verify(repository).findForUpdate(tableId, TENANT_ID, STORE_ID);
    }

    @Test
    void overlappingAssignmentLookupPassesExactUtcHalfOpenRange() {
        ReservationPreassignmentJpaRepository repository = mock(ReservationPreassignmentJpaRepository.class);
        ReservationResourceAssignmentProjection projection = mock(ReservationResourceAssignmentProjection.class);
        UUID reservationId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-15T11:30:00Z");
        Instant end = Instant.parse("2026-07-15T13:00:00Z");
        when(projection.getReservationId()).thenReturn(reservationId);
        when(projection.getReservationCode()).thenReturn("R-ASSIGN-001");
        when(projection.getReservationStatus()).thenReturn("confirmed");
        when(projection.getPartySize()).thenReturn(2);
        when(projection.getReservedStartAt()).thenReturn(start);
        when(projection.getReservedEndAt()).thenReturn(end);
        when(projection.getResourceType()).thenReturn("dining_table");
        when(projection.getResourceId()).thenReturn(tableId);
        when(projection.getResourceCode()).thenReturn("A01");
        when(repository.findActiveResourceAssignmentsOverlapping(
            TENANT_ID,
            STORE_ID,
            LocalDate.of(2026, 7, 15),
            OffsetDateTime.ofInstant(start, ZoneOffset.UTC),
            OffsetDateTime.ofInstant(end, ZoneOffset.UTC)
        )).thenReturn(List.of(projection));
        ReservationPreassignmentPersistenceAdapter adapter = new ReservationPreassignmentPersistenceAdapter(repository);

        Set<ReservationResourceAssignment> assignments = adapter.findActiveResourceAssignmentsOverlapping(
            SCOPE,
            new BusinessDate(LocalDate.of(2026, 7, 15)),
            new TimeRange(start, end)
        );

        assertThat(assignments).extracting(ReservationResourceAssignment::resourceCode).containsExactly("A01");
        verify(repository).findActiveResourceAssignmentsOverlapping(
            TENANT_ID,
            STORE_ID,
            LocalDate.of(2026, 7, 15),
            OffsetDateTime.ofInstant(start, ZoneOffset.UTC),
            OffsetDateTime.ofInstant(end, ZoneOffset.UTC)
        );
    }

    private static ReservationEntity reservationEntity(UUID reservationId) {
        OffsetDateTime start = OffsetDateTime.parse("2026-07-15T11:30:00Z");
        return ReservationEntity.of(
            reservationId,
            TENANT_ID,
            STORE_ID,
            UUID.randomUUID(),
            "R-ASSIGN-001",
            2,
            LocalDate.of(2026, 7, 15),
            start,
            start.plusMinutes(90),
            start.plusMinutes(15),
            "confirmed",
            "public_booking",
            null,
            null,
            null,
            start.minusDays(1),
            start.minusDays(1),
            null,
            0
        );
    }

    private static DiningTableEntity tableEntity(UUID tableId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-15T00:00:00Z");
        return DiningTableEntity.of(
            tableId,
            TENANT_ID,
            STORE_ID,
            UUID.randomUUID(),
            "A01",
            "A01",
            1,
            4,
            "available",
            1,
            false,
            now,
            now,
            null,
            0
        );
    }
}
