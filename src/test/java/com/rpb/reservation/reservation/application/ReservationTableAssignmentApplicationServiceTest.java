package com.rpb.reservation.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.application.port.out.BusinessEventRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.reservation.application.command.AssignReservationTableCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationPreassignmentRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceAssignment;
import com.rpb.reservation.reservation.application.query.AssignableReservationTablesQuery;
import com.rpb.reservation.reservation.application.service.ReservationTableAssignmentApplicationService;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.domain.ReservationPreassignment;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.reservation.value.ReservationCode;
import com.rpb.reservation.reservation.value.ReservationId;
import com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.application.DiningTableResourceRow;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableLockRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReservationTableAssignmentApplicationServiceTest {

    @Test
    void confirmedUnassignedPublicBookingCanAssignTableWithoutChangingReservationStatus() {
        Scenario scenario = Scenario.ready("public_booking");

        ReservationTableAssignmentResult result = scenario.service.assignTable(scenario.command());

        assertThat(result.success()).isTrue();
        assertThat(result.tableCode()).isEqualTo("A01");
        assertThat(scenario.reservation.status()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(scenario.reservationRepository, never()).save(any(), any());
        verify(scenario.preassignmentRepository).save(eq(scenario.scope), any(ReservationPreassignment.class));
    }

    @Test
    void confirmedUnassignedStaffBookingCanAssignTable() {
        Scenario scenario = Scenario.ready("staff");

        ReservationTableAssignmentResult result = scenario.service.assignTable(scenario.command());

        assertThat(result.success()).isTrue();
        assertThat(result.assignmentStatus()).isEqualTo("active");
    }

    @Test
    void arrivedReservationIsRejected() {
        Scenario scenario = Scenario.ready("public_booking");
        scenario.withReservationStatus(ReservationStatus.ARRIVED);

        ReservationTableAssignmentResult result = scenario.service.assignTable(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationTableAssignmentError.RESERVATION_NOT_ASSIGNABLE);
        verify(scenario.preassignmentRepository, never()).save(any(), any());
    }

    @Test
    void sameExistingAssignmentReturnsSuccessWithoutSecondSave() {
        Scenario scenario = Scenario.ready("public_booking");
        when(scenario.preassignmentRepository.findActiveAssignmentForReservation(scenario.scope, scenario.reservationId))
            .thenReturn(Optional.of(scenario.assignment(scenario.tableId, "A01", scenario.start, scenario.end)));

        ReservationTableAssignmentResult result = scenario.service.assignTable(scenario.command());

        assertThat(result.success()).isTrue();
        assertThat(result.tableId()).isEqualTo(scenario.tableId);
        verify(scenario.preassignmentRepository, never()).save(any(), any());
    }

    @Test
    void differentExistingAssignmentIsRejected() {
        Scenario scenario = Scenario.ready("staff");
        when(scenario.preassignmentRepository.findActiveAssignmentForReservation(scenario.scope, scenario.reservationId))
            .thenReturn(Optional.of(scenario.assignment(UUID.randomUUID(), "B02", scenario.start, scenario.end)));

        ReservationTableAssignmentResult result = scenario.service.assignTable(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationTableAssignmentError.RESERVATION_ALREADY_ASSIGNED);
        verify(scenario.preassignmentRepository, never()).save(any(), any());
    }

    @Test
    void overlappingAssignmentIsRejected() {
        Scenario scenario = Scenario.ready("public_booking");
        when(scenario.preassignmentRepository.findActiveResourceAssignmentsOverlapping(
            scenario.scope,
            scenario.businessDate,
            new TimeRange(scenario.start, scenario.end)
        )).thenReturn(Set.of(scenario.assignment(UUID.randomUUID(), scenario.tableId, "A01", scenario.start.plusSeconds(60), scenario.end)));

        ReservationTableAssignmentResult result = scenario.service.assignTable(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationTableAssignmentError.TABLE_NOT_AVAILABLE);
    }

    @Test
    void boundaryTouchingAssignmentDoesNotConflict() {
        Scenario scenario = Scenario.ready("staff");
        when(scenario.preassignmentRepository.findActiveResourceAssignmentsOverlapping(
            scenario.scope,
            scenario.businessDate,
            new TimeRange(scenario.start, scenario.end)
        )).thenReturn(Set.of(scenario.assignment(UUID.randomUUID(), scenario.tableId, "A01", scenario.start.minusSeconds(3600), scenario.start)));

        ReservationTableAssignmentResult result = scenario.service.assignTable(scenario.command());

        assertThat(result.success()).isTrue();
    }

    @Test
    void inactiveAndCapacityMismatchedTablesAreRejected() {
        Scenario inactive = Scenario.ready("staff");
        inactive.withTable(new CapacityRange(1, 4), DiningTableStatus.INACTIVE);
        assertThat(inactive.service.assignTable(inactive.command()).error())
            .isEqualTo(ReservationTableAssignmentError.TABLE_NOT_AVAILABLE);

        Scenario tooSmall = Scenario.ready("staff");
        tooSmall.withTable(new CapacityRange(1, 1), DiningTableStatus.AVAILABLE);
        assertThat(tooSmall.service.assignTable(tooSmall.command()).error())
            .isEqualTo(ReservationTableAssignmentError.TABLE_CAPACITY_INSUFFICIENT);
    }

    @Test
    void crossStoreTableIsNotFound() {
        Scenario scenario = Scenario.ready("public_booking");
        when(scenario.tableRepository.findByIdForUpdate(scenario.scope, new TableId(scenario.tableId))).thenReturn(Optional.empty());

        ReservationTableAssignmentResult result = scenario.service.assignTable(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationTableAssignmentError.TABLE_NOT_FOUND);
    }

    @Test
    void assignmentWritesEventAuditAndCompletedIdempotency() {
        Scenario scenario = Scenario.ready("public_booking");

        ReservationTableAssignmentResult result = scenario.service.assignTable(scenario.command());

        assertThat(result.success()).isTrue();
        ArgumentCaptor<BusinessEvent> event = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(scenario.businessEventRepository).append(eq(scenario.scope), event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("reservation.table_assigned");
        assertThat(event.getValue().metadata()).contains(scenario.tableId.toString(), "A01");
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(scenario.auditLogRepository).append(eq(scenario.scope), audit.capture());
        assertThat(audit.getValue().operationCode()).isEqualTo("reservation.table_assign");
        verify(scenario.idempotencyRepository).complete(eq(scenario.scope), any(IdempotencyRecord.class), eq("reservation"));
    }

    @Test
    void repeatedCompletedCommandReplaysSnapshot() {
        Scenario scenario = Scenario.ready("staff");
        String hash = ReservationTableAssignmentApplicationService.requestHash(scenario.command());
        scenario.withCompletedIdempotency(hash);

        ReservationTableAssignmentResult result = scenario.service.assignTable(scenario.command());

        assertThat(result.success()).isTrue();
        assertThat(result.replayed()).isTrue();
        assertThat(result.tableCode()).isEqualTo("A01");
        verify(scenario.preassignmentRepository, never()).save(any(), any());
    }

    @Test
    void sameKeyDifferentPayloadReturnsIdempotencyConflict() {
        Scenario scenario = Scenario.ready("staff");
        scenario.withCompletedIdempotency("different-request-hash");

        ReservationTableAssignmentResult result = scenario.service.assignTable(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationTableAssignmentError.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void assignableTableQueryFiltersOverlapsInactiveAndCapacityMismatch() {
        Scenario scenario = Scenario.ready("public_booking");
        UUID occupiedId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();
        UUID tooSmallId = UUID.randomUUID();
        when(scenario.tableRepository.findVisibleResourceRows(scenario.scope, null, new PartySize(2))).thenReturn(List.of(
            scenario.row(scenario.tableId, "A01", 1, 4, "available"),
            scenario.row(occupiedId, "A02", 1, 4, "available"),
            scenario.row(inactiveId, "A03", 1, 4, "inactive"),
            scenario.row(tooSmallId, "A04", 1, 1, "available")
        ));
        when(scenario.preassignmentRepository.findActiveResourceAssignmentsOverlapping(
            scenario.scope,
            scenario.businessDate,
            new TimeRange(scenario.start, scenario.end)
        )).thenReturn(Set.of(scenario.assignment(UUID.randomUUID(), occupiedId, "A02", scenario.start, scenario.end)));

        AssignableReservationTablesResult result = scenario.service.listAssignableTables(scenario.query());

        assertThat(result.success()).isTrue();
        assertThat(result.tables()).extracting(AssignableReservationTable::tableCode).containsExactly("A01");
    }

    @Test
    void assignableTableQueryReturnsOnlyCurrentlyAvailableTables() {
        Scenario scenario = Scenario.ready("public_booking");
        UUID lockedId = UUID.randomUUID();
        UUID reservedId = UUID.randomUUID();
        UUID occupiedId = UUID.randomUUID();
        UUID cleaningId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();
        when(scenario.tableRepository.findVisibleResourceRows(scenario.scope, null, new PartySize(2))).thenReturn(List.of(
            scenario.row(scenario.tableId, "A01", 1, 4, "available"),
            scenario.row(lockedId, "A02", 1, 4, "locked"),
            scenario.row(reservedId, "A03", 1, 4, "reserved"),
            scenario.row(occupiedId, "A04", 1, 4, "occupied"),
            scenario.row(cleaningId, "A05", 1, 4, "cleaning"),
            scenario.row(inactiveId, "A06", 1, 4, "inactive")
        ));

        AssignableReservationTablesResult result = scenario.service.listAssignableTables(scenario.query());

        assertThat(result.success()).isTrue();
        assertThat(result.tables()).extracting(AssignableReservationTable::tableCode).containsExactly("A01");
    }

    @Test
    void assignableTableQueryExcludesActiveLockAndOccupancy() {
        Scenario scenario = Scenario.ready("staff");
        UUID lockedId = UUID.randomUUID();
        UUID occupiedId = UUID.randomUUID();
        when(scenario.tableRepository.findVisibleResourceRows(scenario.scope, null, new PartySize(2))).thenReturn(List.of(
            scenario.row(lockedId, "A02", 1, 4, "available"),
            scenario.row(occupiedId, "A03", 1, 4, "available")
        ));
        when(scenario.tableLockRepository.existsActiveConflict(
            eq(scenario.scope), eq("dining_table"), eq(lockedId), any(OffsetDateTime.class)
        )).thenReturn(true);
        when(scenario.seatingRepository.existsActiveResourceOccupancy(
            scenario.scope, "dining_table", occupiedId
        )).thenReturn(true);

        AssignableReservationTablesResult result = scenario.service.listAssignableTables(scenario.query());

        assertThat(result.success()).isTrue();
        assertThat(result.tables()).isEmpty();
    }

    @Test
    void assignmentRejectsTableThatBecameOccupiedAfterListLoad() {
        Scenario scenario = Scenario.ready("staff");
        when(scenario.seatingRepository.existsActiveResourceOccupancy(
            scenario.scope, "dining_table", scenario.tableId
        )).thenReturn(true);

        ReservationTableAssignmentResult result = scenario.service.assignTable(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationTableAssignmentError.TABLE_NOT_AVAILABLE);
        verify(scenario.preassignmentRepository, never()).save(any(), any());
    }

    private static final class Scenario {
        private final UUID tenantId = UUID.randomUUID();
        private final UUID storeId = UUID.randomUUID();
        private final UUID reservationId = UUID.randomUUID();
        private final UUID tableId = UUID.randomUUID();
        private final UUID actorId = UUID.randomUUID();
        private final UUID areaId = UUID.randomUUID();
        private final Instant start = Instant.parse("2026-07-15T11:30:00Z");
        private final Instant end = Instant.parse("2026-07-15T13:00:00Z");
        private final BusinessDate businessDate = new BusinessDate(LocalDate.of(2026, 7, 15));
        private final StoreScope scope = new StoreScope(new TenantId(tenantId), new StoreId(storeId));
        private final StoreRepositoryPort storeRepository = mock(StoreRepositoryPort.class);
        private final ReservationRepositoryPort reservationRepository = mock(ReservationRepositoryPort.class);
        private final DiningTableRepositoryPort tableRepository = mock(DiningTableRepositoryPort.class);
        private final TableLockRepositoryPort tableLockRepository = mock(TableLockRepositoryPort.class);
        private final ReservationPreassignmentRepositoryPort preassignmentRepository = mock(ReservationPreassignmentRepositoryPort.class);
        private final SeatingRepositoryPort seatingRepository = mock(SeatingRepositoryPort.class);
        private final BusinessEventRepositoryPort businessEventRepository = mock(BusinessEventRepositoryPort.class);
        private final AuditLogRepositoryPort auditLogRepository = mock(AuditLogRepositoryPort.class);
        private final IdempotencyRepositoryPort idempotencyRepository = mock(IdempotencyRepositoryPort.class);
        private final Clock clock = Clock.fixed(Instant.parse("2026-07-15T08:00:00Z"), ZoneOffset.UTC);
        private final ReservationTableAssignmentApplicationService service = new ReservationTableAssignmentApplicationService(
            storeRepository,
            reservationRepository,
            tableRepository,
            tableLockRepository,
            preassignmentRepository,
            seatingRepository,
            businessEventRepository,
            auditLogRepository,
            idempotencyRepository,
            clock
        );
        private Reservation reservation;
        private DiningTable table;

        private static Scenario ready(String source) {
            Scenario scenario = new Scenario();
            Store store = Store.skeleton(
                new StoreId(scenario.storeId),
                new TenantId(scenario.tenantId),
                "SG-01",
                "Asia/Singapore",
                "zh-CN",
                "active"
            );
            scenario.reservation = scenario.reservation(source, ReservationStatus.CONFIRMED);
            scenario.table = scenario.table(new CapacityRange(1, 4), DiningTableStatus.AVAILABLE);
            when(scenario.storeRepository.findById(scenario.scope)).thenReturn(Optional.of(store));
            when(scenario.reservationRepository.findById(scenario.scope, new ReservationId(scenario.reservationId)))
                .thenAnswer(ignored -> Optional.of(scenario.reservation));
            when(scenario.reservationRepository.findByIdForUpdate(scenario.scope, new ReservationId(scenario.reservationId)))
                .thenAnswer(ignored -> Optional.of(scenario.reservation));
            when(scenario.tableRepository.findByIdForUpdate(scenario.scope, new TableId(scenario.tableId)))
                .thenAnswer(ignored -> Optional.of(scenario.table));
            when(scenario.preassignmentRepository.findActiveAssignmentForReservation(scenario.scope, scenario.reservationId))
                .thenReturn(Optional.empty());
            when(scenario.preassignmentRepository.findActiveResourceAssignmentsOverlapping(
                scenario.scope,
                scenario.businessDate,
                new TimeRange(scenario.start, scenario.end)
            )).thenReturn(Set.of());
            when(scenario.preassignmentRepository.save(eq(scenario.scope), any(ReservationPreassignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
            when(scenario.businessEventRepository.append(eq(scenario.scope), any(BusinessEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
            when(scenario.auditLogRepository.append(eq(scenario.scope), any(AuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
            when(scenario.idempotencyRepository.findByScopeActionKey(
                eq(scenario.scope),
                eq("staff"),
                eq("assign_reservation_table"),
                any(IdempotencyKey.class)
            )).thenReturn(Optional.empty());
            when(scenario.idempotencyRepository.start(
                eq(scenario.scope),
                eq("staff"),
                eq("assign_reservation_table"),
                any(IdempotencyKey.class),
                any(String.class),
                any(OffsetDateTime.class)
            )).thenAnswer(invocation -> new IdempotencyRecord(
                UUID.randomUUID(),
                invocation.getArgument(3),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(4),
                IdempotencyStatus.STARTED,
                null,
                null,
                null
            ));
            when(scenario.idempotencyRepository.complete(eq(scenario.scope), any(IdempotencyRecord.class), eq("reservation")))
                .thenAnswer(invocation -> {
                    IdempotencyRecord input = invocation.getArgument(1);
                    return new IdempotencyRecord(
                        input.id(), input.idempotencyKey(), input.source(), input.action(), input.requestHash(),
                        IdempotencyStatus.COMPLETED, "reservation", input.targetId(), input.responseSnapshot()
                    );
                });
            return scenario;
        }

        private AssignReservationTableCommand command() {
            return new AssignReservationTableCommand(
                tenantId, storeId, reservationId, tableId, "assign-table-1", actorId, "store_staff", "staff"
            );
        }

        private AssignableReservationTablesQuery query() {
            return new AssignableReservationTablesQuery(tenantId, storeId, reservationId, actorId, "store_staff");
        }

        private void withReservationStatus(ReservationStatus status) {
            reservation = reservation(reservation.sourceChannel(), status);
        }

        private void withTable(CapacityRange capacity, DiningTableStatus status) {
            table = table(capacity, status);
        }

        private void withCompletedIdempotency(String requestHash) {
            IdempotencyRecord completed = new IdempotencyRecord(
                UUID.randomUUID(),
                new IdempotencyKey("assign-table-1"),
                "staff",
                "assign_reservation_table",
                requestHash,
                IdempotencyStatus.COMPLETED,
                "reservation",
                reservationId,
                "{\"reservationId\":\"%s\",\"tableId\":\"%s\",\"tableCode\":\"A01\",\"assignmentStatus\":\"active\"}"
                    .formatted(reservationId, tableId)
            );
            when(idempotencyRepository.findByScopeActionKey(
                eq(scope), eq("staff"), eq("assign_reservation_table"), any(IdempotencyKey.class)
            )).thenReturn(Optional.of(completed));
        }

        private Reservation reservation(String source, ReservationStatus status) {
            return new Reservation(
                new ReservationId(reservationId),
                scope,
                null,
                new ReservationCode("R-ASSIGN-001"),
                new PartySize(2),
                businessDate,
                start,
                end,
                null,
                status,
                source,
                null,
                null,
                null,
                start.minusSeconds(86400),
                start.minusSeconds(86400),
                null
            );
        }

        private DiningTable table(CapacityRange capacity, DiningTableStatus status) {
            return new DiningTable(new TableId(tableId), scope, areaId, "A01", capacity, status, false);
        }

        private ReservationResourceAssignment assignment(UUID otherReservationId, UUID assignedTableId, String code, Instant assignedStart, Instant assignedEnd) {
            return new ReservationResourceAssignment(
                otherReservationId,
                "R-OTHER",
                "confirmed",
                2,
                assignedStart,
                assignedEnd,
                null,
                null,
                "dining_table",
                assignedTableId,
                code,
                null,
                null,
                null
            );
        }

        private ReservationResourceAssignment assignment(UUID assignedTableId, String code, Instant assignedStart, Instant assignedEnd) {
            return assignment(reservationId, assignedTableId, code, assignedStart, assignedEnd);
        }

        private DiningTableResourceRow row(UUID id, String code, int min, int max, String status) {
            return new DiningTableResourceRow(id, code, code, "Main", min, max, status);
        }
    }
}
