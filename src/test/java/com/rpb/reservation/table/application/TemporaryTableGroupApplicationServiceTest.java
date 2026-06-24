package com.rpb.reservation.table.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.reservation.application.port.out.ReservationPreassignmentRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceAssignment;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceTarget;
import com.rpb.reservation.reservation.domain.ReservationPreassignment;
import com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort;
import com.rpb.reservation.seating.domain.Seating;
import com.rpb.reservation.seating.domain.SeatingResource;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.reservation.value.ReservationId;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableLockRepositoryPort;
import com.rpb.reservation.table.application.service.TemporaryTableGroupApplicationService;
import com.rpb.reservation.table.application.service.TemporaryTableGroupCommand;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.domain.TableLock;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.status.TableGroupStatus;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemporaryTableGroupApplicationServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID AREA_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TABLE_A_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID TABLE_B_ID = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final StoreScope SCOPE = new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));
    private static final BusinessDate BUSINESS_DATE = new BusinessDate(LocalDate.of(2026, 6, 24));
    private static final Clock CLOCK = Clock.fixed(OffsetDateTime.parse("2026-06-24T10:00:00Z").toInstant(), ZoneOffset.UTC);

    @Test
    void createsOccupiedTemporaryGroupWithMembersAndCombinedCapacity() {
        Scenario scenario = Scenario.ready();

        TemporaryTableGroupResult result = scenario.service().createForSeating(command(6, null, TABLE_A_ID, TABLE_B_ID));

        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.group().groupType()).isEqualTo("temporary");
        assertThat(result.group().status()).isEqualTo(TableGroupStatus.OCCUPIED);
        assertThat(result.group().capacity()).isEqualTo(new CapacityRange(4, 8));
        assertThat(result.memberTables()).extracting(table -> table.id().value()).containsExactly(TABLE_A_ID, TABLE_B_ID);
        assertThat(scenario.tableGroupRepository.saved).containsExactly(result.group());
        assertThat(scenario.tableGroupRepository.members)
            .extracting(member -> member.tableId().value())
            .containsExactly(TABLE_A_ID, TABLE_B_ID);
    }

    @Test
    void requiresAtLeastTwoDistinctMemberTables() {
        Scenario scenario = Scenario.ready();

        assertThat(scenario.service().createForSeating(command(4, null, TABLE_A_ID)).error())
            .isEqualTo(TemporaryTableGroupError.MEMBER_REQUIRED);
        assertThat(scenario.service().createForSeating(command(4, null, TABLE_A_ID, TABLE_A_ID)).error())
            .isEqualTo(TemporaryTableGroupError.MEMBER_DUPLICATE);
        assertThat(scenario.tableGroupRepository.saved).isEmpty();
    }

    @Test
    void rejectsUnavailableOrNonCombinableMembers() {
        Scenario scenario = Scenario.ready();
        scenario.diningTableRepository.tables.put(TABLE_B_ID, table(TABLE_B_ID, DiningTableStatus.OCCUPIED, true));

        assertThat(scenario.service().createForSeating(command(6, null, TABLE_A_ID, TABLE_B_ID)).error())
            .isEqualTo(TemporaryTableGroupError.MEMBER_UNAVAILABLE);

        scenario = Scenario.ready();
        scenario.diningTableRepository.tables.put(TABLE_B_ID, table(TABLE_B_ID, DiningTableStatus.AVAILABLE, false));

        assertThat(scenario.service().createForSeating(command(6, null, TABLE_A_ID, TABLE_B_ID)).error())
            .isEqualTo(TemporaryTableGroupError.MEMBER_UNAVAILABLE);
    }

    @Test
    void rejectsPartySizeOutsideCombinedCapacity() {
        Scenario scenario = Scenario.ready();

        TemporaryTableGroupResult result = scenario.service().createForSeating(command(9, null, TABLE_A_ID, TABLE_B_ID));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(TemporaryTableGroupError.CAPACITY_INSUFFICIENT);
        assertThat(scenario.tableGroupRepository.saved).isEmpty();
    }

    @Test
    void rejectsMemberLockConflict() {
        Scenario scenario = Scenario.ready();
        scenario.tableLockRepository.conflictedResourceIds.add(TABLE_B_ID);

        TemporaryTableGroupResult result = scenario.service().createForSeating(command(6, null, TABLE_A_ID, TABLE_B_ID));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(TemporaryTableGroupError.LOCK_CONFLICT);
        assertThat(scenario.tableGroupRepository.saved).isEmpty();
    }

    @Test
    void rejectsMemberReservationPreassignmentConflict() {
        Scenario scenario = Scenario.ready();
        scenario.preassignmentRepository.assign(resourceAssignment(TABLE_B_ID));

        TemporaryTableGroupResult result = scenario.service().createForSeating(command(6, RESERVATION_ID, TABLE_A_ID, TABLE_B_ID));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(TemporaryTableGroupError.PREASSIGNMENT_CONFLICT);
        assertThat(scenario.tableGroupRepository.saved).isEmpty();
    }

    @Test
    void rejectsActiveMemberTableOccupancy() {
        Scenario scenario = Scenario.ready();
        scenario.seatingRepository.occupiedResourceIds.add(TABLE_A_ID);

        TemporaryTableGroupResult result = scenario.service().createForSeating(command(6, null, TABLE_A_ID, TABLE_B_ID));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(TemporaryTableGroupError.MEMBER_UNAVAILABLE);
        assertThat(scenario.tableGroupRepository.saved).isEmpty();
    }

    private static TemporaryTableGroupCommand command(int partySize, UUID sourceReservationId, UUID... tableIds) {
        return new TemporaryTableGroupCommand(
            SCOPE,
            List.of(tableIds),
            new PartySize(partySize),
            BUSINESS_DATE,
            sourceReservationId
        );
    }

    private static DiningTable table(UUID id, DiningTableStatus status, boolean combinable) {
        return new DiningTable(
            new TableId(id),
            SCOPE,
            AREA_ID,
            "T-" + id.toString().substring(0, 4),
            new CapacityRange(2, 4),
            status,
            combinable
        );
    }

    private static ReservationResourceAssignment resourceAssignment(UUID resourceId) {
        return new ReservationResourceAssignment(
            UUID.randomUUID(),
            "R-1001",
            "confirmed",
            4,
            CLOCK.instant(),
            CLOCK.instant().plusSeconds(3600),
            "Guest",
            null,
            "dining_table",
            resourceId,
            "A01",
            null,
            null,
            null
        );
    }

    private static final class Scenario {
        final FakeDiningTableRepository diningTableRepository = new FakeDiningTableRepository();
        final FakeTableGroupRepository tableGroupRepository = new FakeTableGroupRepository();
        final FakeTableLockRepository tableLockRepository = new FakeTableLockRepository();
        final FakePreassignmentRepository preassignmentRepository = new FakePreassignmentRepository();
        final FakeSeatingRepository seatingRepository = new FakeSeatingRepository();

        static Scenario ready() {
            Scenario scenario = new Scenario();
            scenario.diningTableRepository.tables.put(TABLE_A_ID, table(TABLE_A_ID, DiningTableStatus.AVAILABLE, true));
            scenario.diningTableRepository.tables.put(TABLE_B_ID, table(TABLE_B_ID, DiningTableStatus.AVAILABLE, true));
            return scenario;
        }

        TemporaryTableGroupApplicationService service() {
            return new TemporaryTableGroupApplicationService(
                diningTableRepository,
                tableGroupRepository,
                tableLockRepository,
                preassignmentRepository,
                seatingRepository,
                CLOCK
            );
        }
    }

    private static final class FakeDiningTableRepository implements DiningTableRepositoryPort {
        final Map<UUID, DiningTable> tables = new HashMap<>();

        @Override
        public Optional<DiningTable> findById(StoreScope scope, TableId tableId) {
            return Optional.ofNullable(tables.get(tableId.value())).filter(table -> table.scope().equals(scope));
        }

        @Override
        public List<DiningTable> findActiveByArea(StoreScope scope, UUID areaId) {
            return tables.values().stream().filter(table -> table.scope().equals(scope) && table.areaId().equals(areaId)).toList();
        }

        @Override
        public List<DiningTable> findCandidates(StoreScope scope, PartySize partySize, BusinessDate businessDate) {
            return tables.values().stream().filter(table -> table.scope().equals(scope)).toList();
        }

        @Override
        public DiningTable save(StoreScope scope, DiningTable table) {
            tables.put(table.id().value(), table);
            return table;
        }
    }

    private static final class FakeTableGroupRepository implements TableGroupRepositoryPort {
        final Map<UUID, TableGroup> groups = new HashMap<>();
        final List<TableGroup> saved = new ArrayList<>();
        final List<TableGroupMember> members = new ArrayList<>();

        @Override
        public Optional<TableGroup> findById(StoreScope scope, TableGroupId tableGroupId) {
            return Optional.ofNullable(groups.get(tableGroupId.value())).filter(group -> group.scope().equals(scope));
        }

        @Override
        public List<TableGroupMember> findActiveMembers(StoreScope scope, TableGroupId tableGroupId) {
            return members.stream().filter(member -> member.scope().equals(scope) && member.tableGroupId().equals(tableGroupId)).toList();
        }

        @Override
        public List<TableGroup> findActiveGroupsForTable(StoreScope scope, TableId tableId) {
            return List.of();
        }

        @Override
        public List<TableGroup> findCandidates(StoreScope scope, PartySize partySize, BusinessDate businessDate) {
            return groups.values().stream().filter(group -> group.scope().equals(scope)).toList();
        }

        @Override
        public TableGroup save(StoreScope scope, TableGroup tableGroup) {
            saved.add(tableGroup);
            groups.put(tableGroup.id().value(), tableGroup);
            return tableGroup;
        }

        @Override
        public TableGroupMember saveMember(StoreScope scope, TableGroupMember member) {
            members.add(member);
            return member;
        }
    }

    private static final class FakeTableLockRepository implements TableLockRepositoryPort {
        final Set<UUID> conflictedResourceIds = new HashSet<>();

        @Override
        public Optional<TableLock> findActiveByResource(StoreScope scope, String resourceType, UUID resourceId) {
            return Optional.empty();
        }

        @Override
        public boolean existsActiveConflict(StoreScope scope, String resourceType, UUID resourceId, OffsetDateTime at) {
            return "dining_table".equals(resourceType) && conflictedResourceIds.contains(resourceId);
        }

        @Override
        public TableLock save(StoreScope scope, TableLock lock) {
            return lock;
        }

        @Override
        public TableLock release(StoreScope scope, UUID tableLockId, OffsetDateTime releasedAt) {
            return null;
        }
    }

    private static final class FakePreassignmentRepository implements ReservationPreassignmentRepositoryPort {
        final Map<ReservationResourceTarget, ReservationResourceAssignment> byResource = new HashMap<>();

        void assign(ReservationResourceAssignment assignment) {
            byResource.put(assignment.target(), assignment);
        }

        @Override
        public boolean existsActiveResourceConflict(
            StoreScope scope,
            String resourceType,
            UUID resourceId,
            BusinessDate businessDate,
            TimeRange timeRange
        ) {
            return byResource.containsKey(new ReservationResourceTarget(resourceType, resourceId));
        }

        @Override
        public Set<ReservationResourceAssignment> findActiveResourceAssignmentsForDate(StoreScope scope, BusinessDate businessDate) {
            return Set.copyOf(byResource.values());
        }

        @Override
        public Optional<ReservationResourceAssignment> findActiveAssignmentForResource(
            StoreScope scope,
            String resourceType,
            UUID resourceId,
            BusinessDate businessDate
        ) {
            return Optional.ofNullable(byResource.get(new ReservationResourceTarget(resourceType, resourceId)));
        }

        @Override
        public ReservationPreassignment save(StoreScope scope, ReservationPreassignment preassignment) {
            return preassignment;
        }
    }

    private static final class FakeSeatingRepository implements SeatingRepositoryPort {
        final Set<UUID> occupiedResourceIds = new HashSet<>();

        @Override
        public Optional<Seating> findById(StoreScope scope, SeatingId seatingId) {
            return Optional.empty();
        }

        @Override
        public Optional<Seating> findActiveBySource(StoreScope scope, String sourceType, UUID sourceId) {
            return Optional.empty();
        }

        @Override
        public Optional<Seating> findCurrentByReservation(StoreScope scope, ReservationId reservationId) {
            return Optional.empty();
        }

        @Override
        public boolean existsActiveResourceOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
            return "dining_table".equals(resourceType) && occupiedResourceIds.contains(resourceId);
        }

        @Override
        public Optional<Seating> findActiveOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
            return Optional.empty();
        }

        @Override
        public Optional<SeatingResource> findActiveResourceBySeating(StoreScope scope, SeatingId seatingId) {
            return Optional.empty();
        }

        @Override
        public Seating save(StoreScope scope, Seating seating) {
            return seating;
        }

        @Override
        public SeatingResource saveResource(StoreScope scope, SeatingResource resource) {
            return resource;
        }
    }
}
