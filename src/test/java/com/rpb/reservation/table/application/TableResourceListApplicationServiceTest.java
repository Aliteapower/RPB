package com.rpb.reservation.table.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.cleaning.application.port.out.CleaningRepositoryPort;
import com.rpb.reservation.cleaning.domain.Cleaning;
import com.rpb.reservation.cleaning.status.CleaningStatus;
import com.rpb.reservation.cleaning.value.CleaningId;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.reservation.application.port.out.ReservationPreassignmentRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceAssignment;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceTarget;
import com.rpb.reservation.reservation.domain.ReservationPreassignment;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort;
import com.rpb.reservation.seating.domain.Seating;
import com.rpb.reservation.seating.domain.SeatingResource;
import com.rpb.reservation.seating.status.SeatingStatus;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.table.application.service.TableResourceListApplicationService;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.status.TableGroupStatus;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TableResourceListApplicationServiceTest {
    private static final String DINING_TABLE_TYPE = "dining_table";
    private static final String TABLE_GROUP_TYPE = "table_group";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000001201");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000001201");
    private static final UUID AREA_ID = UUID.fromString("60000000-0000-0000-0000-000000001201");
    private static final TableId TABLE_A1_ID = new TableId(UUID.fromString("70000000-0000-0000-0000-000000001201"));
    private static final TableId TABLE_A2_ID = new TableId(UUID.fromString("70000000-0000-0000-0000-000000001202"));
    private static final TableGroupId GROUP_VIP_ID = new TableGroupId(UUID.fromString("71000000-0000-0000-0000-000000001201"));
    private static final SeatingId SEATING_ID = new SeatingId(UUID.fromString("80000000-0000-0000-0000-000000001201"));
    private static final CleaningId CLEANING_ID = new CleaningId(UUID.fromString("81000000-0000-0000-0000-000000001201"));
    private static final StoreScope SCOPE = new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));

    @Test
    void listsConfiguredTableNumbersAndGroupsForSelection() {
        FakeDiningTableRepository diningTables = new FakeDiningTableRepository();
        diningTables.rows.add(tableRow(TABLE_A1_ID, "A01", "A01 靠窗", "A区", 1, 4, "available"));
        diningTables.rows.add(tableRow(TABLE_A2_ID, "A02", "A02 中央", "A区", 2, 6, "occupied"));
        diningTables.rows.add(tableRow(new TableId(UUID.fromString("70000000-0000-0000-0000-000000001203")), "A03", "A03 吧台", "A区", 1, 2, "cleaning"));
        diningTables.tables.add(table(TABLE_A1_ID, "A01", 1, 4, DiningTableStatus.AVAILABLE));
        diningTables.tables.add(table(TABLE_A2_ID, "A02", 2, 6, DiningTableStatus.OCCUPIED));
        FakeTableGroupRepository tableGroups = new FakeTableGroupRepository();
        tableGroups.groups.add(group(GROUP_VIP_ID, "VIP-1", 8, 12, TableGroupStatus.ACTIVE));
        tableGroups.members.add(member(GROUP_VIP_ID, TABLE_A1_ID));
        tableGroups.members.add(member(GROUP_VIP_ID, TABLE_A2_ID));
        UUID reservationId = UUID.fromString("50000000-0000-0000-0000-000000001201");
        FakeSeatingRepository seatings = new FakeSeatingRepository();
        seatings.activeOccupancies.add(new ActiveOccupancy(
            DINING_TABLE_TYPE,
            TABLE_A2_ID.value(),
            new Seating(SEATING_ID, SCOPE, "reservation", reservationId, "S-1", new PartySize(4), SeatingStatus.OCCUPIED)
        ));
        FakeCleaningRepository cleanings = new FakeCleaningRepository();
        cleanings.activeCleanings.add(new Cleaning(CLEANING_ID, SCOPE, SEATING_ID, DINING_TABLE_TYPE, UUID.fromString("70000000-0000-0000-0000-000000001203"), CleaningStatus.CLEANING));

        TableResourceListApplicationService service = new TableResourceListApplicationService(
            diningTables,
            tableGroups,
            seatings,
            cleanings,
            new FakeReservationPreassignmentRepository()
        );

        TableResourceListResult result = service.listResources(new TableResourceListQuery(SCOPE, null, null, true, null));

        assertThat(result.success()).isTrue();
        assertThat(result.resources()).extracting(TableResourceItem::code)
            .containsExactly("A01", "A02", "A03", "VIP-1");
        assertThat(result.resources().get(0).resourceType()).isEqualTo("dining_table");
        assertThat(result.resources().get(0).displayName()).isEqualTo("A01 靠窗");
        assertThat(result.resources().get(0).areaName()).isEqualTo("A区");
        assertThat(result.resources().get(0).selectable()).isTrue();
        assertThat(result.resources().get(1).selectable()).isFalse();
        assertThat(result.resources().get(1).displayName()).isEqualTo("A02 中央");
        assertThat(result.resources().get(1).areaName()).isEqualTo("A区");
        assertThat(result.resources().get(1).selectionDisabledReason()).isEqualTo("status_unavailable");
        assertThat(result.resources().get(1).currentSeatingId()).isEqualTo(SEATING_ID.value());
        assertThat(result.resources().get(1).currentReservationId()).isEqualTo(reservationId);
        assertThat(result.resources().get(1).currentPartySize()).isEqualTo(4);
        assertThat(result.resources().get(2).currentCleaningId()).isEqualTo(CLEANING_ID.value());
        assertThat(result.resources().get(3).resourceType()).isEqualTo("table_group");
        assertThat(result.resources().get(3).memberTableCodes()).containsExactly("A01", "A02");
    }

    @Test
    void createdTemporaryGroupIsSelectableAndMemberTablesAreBlockedAsSingleTables() {
        FakeDiningTableRepository diningTables = new FakeDiningTableRepository();
        diningTables.rows.add(tableRow(TABLE_A1_ID, "A01", "A01 靠窗", "A区", 2, 4, "available"));
        diningTables.rows.add(tableRow(TABLE_A2_ID, "A02", "A02 中央", "A区", 2, 4, "available"));
        diningTables.tables.add(table(TABLE_A1_ID, "A01", 2, 4, DiningTableStatus.AVAILABLE));
        diningTables.tables.add(table(TABLE_A2_ID, "A02", 2, 4, DiningTableStatus.AVAILABLE));
        FakeTableGroupRepository tableGroups = new FakeTableGroupRepository();
        tableGroups.groups.add(new TableGroup(
            GROUP_VIP_ID,
            SCOPE,
            "A区临组1",
            "temporary",
            new CapacityRange(4, 8),
            TableGroupStatus.CREATED
        ));
        tableGroups.members.add(member(GROUP_VIP_ID, TABLE_A1_ID));
        tableGroups.members.add(member(GROUP_VIP_ID, TABLE_A2_ID));
        TableResourceListApplicationService service = new TableResourceListApplicationService(
            diningTables,
            tableGroups,
            new FakeSeatingRepository(),
            new FakeCleaningRepository(),
            new FakeReservationPreassignmentRepository()
        );

        TableResourceListResult result = service.listResources(new TableResourceListQuery(SCOPE, null, null, true, null));

        assertThat(result.success()).isTrue();
        assertThat(result.resources()).extracting(TableResourceItem::code).containsExactly("A01", "A02", "A区临组1");
        assertThat(result.resources().get(0).selectable()).isFalse();
        assertThat(result.resources().get(0).selectionDisabledReason()).isEqualTo("temporary_group_member");
        assertThat(result.resources().get(2).groupType()).isEqualTo("temporary");
        assertThat(result.resources().get(2).status()).isEqualTo("created");
        assertThat(result.resources().get(2).selectable()).isTrue();
        assertThat(result.resources().get(2).memberTableCodes()).containsExactly("A01", "A02");
    }

    @Test
    void activeSeatingOccupancyTurnsCreatedTemporaryGroupIntoOccupiedResource() {
        FakeDiningTableRepository diningTables = new FakeDiningTableRepository();
        diningTables.rows.add(tableRow(TABLE_A1_ID, "A01", "A01 靠窗", "A区", 2, 4, "occupied"));
        diningTables.rows.add(tableRow(TABLE_A2_ID, "A02", "A02 中央", "A区", 2, 4, "occupied"));
        diningTables.tables.add(table(TABLE_A1_ID, "A01", 2, 4, DiningTableStatus.OCCUPIED));
        diningTables.tables.add(table(TABLE_A2_ID, "A02", 2, 4, DiningTableStatus.OCCUPIED));
        FakeTableGroupRepository tableGroups = new FakeTableGroupRepository();
        tableGroups.groups.add(new TableGroup(
            GROUP_VIP_ID,
            SCOPE,
            "A区临组1",
            "temporary",
            new CapacityRange(4, 8),
            TableGroupStatus.CREATED
        ));
        tableGroups.members.add(member(GROUP_VIP_ID, TABLE_A1_ID));
        tableGroups.members.add(member(GROUP_VIP_ID, TABLE_A2_ID));
        FakeSeatingRepository seatings = new FakeSeatingRepository();
        seatings.activeOccupancies.add(new ActiveOccupancy(
            TABLE_GROUP_TYPE,
            GROUP_VIP_ID.value(),
            new Seating(SEATING_ID, SCOPE, "queue_ticket", UUID.randomUUID(), "S-1", new PartySize(4), SeatingStatus.OCCUPIED)
        ));
        TableResourceListApplicationService service = new TableResourceListApplicationService(
            diningTables,
            tableGroups,
            seatings,
            new FakeCleaningRepository(),
            new FakeReservationPreassignmentRepository()
        );

        TableResourceListResult result = service.listResources(new TableResourceListQuery(SCOPE, null, null, true, null));

        assertThat(result.success()).isTrue();
        TableResourceItem group = result.resources().getLast();
        assertThat(group.groupType()).isEqualTo("temporary");
        assertThat(group.status()).isEqualTo("occupied");
        assertThat(group.selectable()).isFalse();
        assertThat(group.selectionDisabledReason()).isEqualTo("status_unavailable");
        assertThat(group.currentSeatingId()).isEqualTo(SEATING_ID.value());
        assertThat(group.currentPartySize()).isEqualTo(4);

        TableResourceListResult occupiedResult = service.listResources(new TableResourceListQuery(SCOPE, "occupied", null, true, null));

        assertThat(occupiedResult.success()).isTrue();
        assertThat(occupiedResult.resources()).extracting(TableResourceItem::code)
            .contains("A区临组1");
    }

    @Test
    void doesNotExposeCompletedSeatingAsSwitchableCurrentOccupancy() {
        FakeDiningTableRepository diningTables = new FakeDiningTableRepository();
        diningTables.rows.add(tableRow(TABLE_A1_ID, "A01", "A01", "A区", 1, 4, "occupied"));
        UUID reservationId = UUID.fromString("50000000-0000-0000-0000-000000001299");
        FakeSeatingRepository seatings = new FakeSeatingRepository();
        seatings.activeOccupancies.add(new ActiveOccupancy(
            DINING_TABLE_TYPE,
            TABLE_A1_ID.value(),
            new Seating(SEATING_ID, SCOPE, "reservation", reservationId, "S-1", new PartySize(2), SeatingStatus.COMPLETED)
        ));

        TableResourceListApplicationService service = new TableResourceListApplicationService(
            diningTables,
            new FakeTableGroupRepository(),
            seatings,
            new FakeCleaningRepository(),
            new FakeReservationPreassignmentRepository()
        );

        TableResourceListResult result = service.listResources(new TableResourceListQuery(SCOPE, null, null, false, null));

        assertThat(result.success()).isTrue();
        assertThat(result.resources()).hasSize(1);
        TableResourceItem table = result.resources().getFirst();
        assertThat(table.status()).isEqualTo("occupied");
        assertThat(table.selectable()).isFalse();
        assertThat(table.currentSeatingId()).isNull();
        assertThat(table.currentReservationId()).isNull();
        assertThat(table.currentPartySize()).isNull();
    }

    @Test
    void futureBusinessDateTreatsCurrentOccupancyAndCleaningAsAvailableWhenNotPreassigned() {
        FakeDiningTableRepository diningTables = new FakeDiningTableRepository();
        diningTables.rows.add(tableRow(TABLE_A1_ID, "A01", "A01", "A区", 1, 4, "occupied"));
        diningTables.rows.add(tableRow(TABLE_A2_ID, "A02", "A02", "A区", 2, 6, "cleaning"));
        diningTables.rows.add(tableRow(new TableId(UUID.fromString("70000000-0000-0000-0000-000000001204")), "A03", "A03", "A区", 2, 6, "inactive"));
        FakeSeatingRepository seatings = new FakeSeatingRepository();
        seatings.activeOccupancies.add(new ActiveOccupancy(
            DINING_TABLE_TYPE,
            TABLE_A1_ID.value(),
            new Seating(SEATING_ID, SCOPE, "walk_in", UUID.randomUUID(), "S-1", new PartySize(4), SeatingStatus.OCCUPIED)
        ));
        FakeCleaningRepository cleanings = new FakeCleaningRepository();
        cleanings.activeCleanings.add(new Cleaning(CLEANING_ID, SCOPE, SEATING_ID, DINING_TABLE_TYPE, TABLE_A2_ID.value(), CleaningStatus.CLEANING));
        TableResourceListApplicationService service = new TableResourceListApplicationService(
            diningTables,
            new FakeTableGroupRepository(),
            seatings,
            cleanings,
            new FakeReservationPreassignmentRepository(),
            new FakeStoreRepository(),
            Clock.fixed(Instant.parse("2026-06-24T02:00:00Z"), ZoneOffset.UTC)
        );

        TableResourceListResult result = service.listResources(
            new TableResourceListQuery(SCOPE, null, null, false, new BusinessDate(LocalDate.of(2026, 6, 30)))
        );

        assertThat(result.success()).isTrue();
        assertThat(result.resources()).extracting(TableResourceItem::code).containsExactly("A01", "A02", "A03");
        assertThat(result.resources().get(0).status()).isEqualTo("available");
        assertThat(result.resources().get(0).selectable()).isTrue();
        assertThat(result.resources().get(0).currentSeatingId()).isNull();
        assertThat(result.resources().get(1).status()).isEqualTo("available");
        assertThat(result.resources().get(1).selectable()).isTrue();
        assertThat(result.resources().get(1).currentCleaningId()).isNull();
        assertThat(result.resources().get(2).status()).isEqualTo("inactive");
        assertThat(result.resources().get(2).selectable()).isFalse();

        TableResourceListResult availableResult = service.listResources(
            new TableResourceListQuery(SCOPE, "available", null, false, new BusinessDate(LocalDate.of(2026, 6, 30)))
        );

        assertThat(availableResult.success()).isTrue();
        assertThat(availableResult.resources()).extracting(TableResourceItem::code).containsExactly("A01", "A02");
    }

    @Test
    void filtersByStatusAndPartySizeWithoutMutatingResources() {
        FakeDiningTableRepository diningTables = new FakeDiningTableRepository();
        diningTables.rows.add(tableRow(TABLE_A1_ID, "A01", "A01", "A区", 1, 4, "available"));
        diningTables.rows.add(tableRow(TABLE_A2_ID, "A02", "A02", "A区", 2, 6, "occupied"));
        FakeTableGroupRepository tableGroups = new FakeTableGroupRepository();
        tableGroups.groups.add(group(GROUP_VIP_ID, "VIP-1", 8, 12, TableGroupStatus.ACTIVE));

        TableResourceListApplicationService service = new TableResourceListApplicationService(
            diningTables,
            tableGroups,
            new FakeSeatingRepository(),
            new FakeCleaningRepository(),
            new FakeReservationPreassignmentRepository()
        );

        TableResourceListResult result = service.listResources(new TableResourceListQuery(SCOPE, "available", 4, true, null));

        assertThat(result.success()).isTrue();
        assertThat(result.resources()).extracting(TableResourceItem::code).containsExactly("A01");
        assertThat(diningTables.saveCalls).isZero();
        assertThat(tableGroups.saveCalls).isZero();
    }

    @Test
    void returnsEmptyListWhenBackendTableSetupHasNoTablesOrGroups() {
        TableResourceListApplicationService service = new TableResourceListApplicationService(
            new FakeDiningTableRepository(),
            new FakeTableGroupRepository(),
            new FakeSeatingRepository(),
            new FakeCleaningRepository(),
            new FakeReservationPreassignmentRepository()
        );

        TableResourceListResult result = service.listResources(new TableResourceListQuery(SCOPE, null, null, true, null));

        assertThat(result.success()).isTrue();
        assertThat(result.resources()).isEmpty();
    }

    @Test
    void overlaysActiveReservationPreassignmentsForSelectedBusinessDate() {
        FakeDiningTableRepository diningTables = new FakeDiningTableRepository();
        diningTables.rows.add(tableRow(TABLE_A1_ID, "A01", "A01", "A区", 1, 4, "available"));
        diningTables.rows.add(tableRow(TABLE_A2_ID, "A02", "A02", "A区", 2, 6, "available"));
        FakeReservationPreassignmentRepository preassignments = new FakeReservationPreassignmentRepository();
        UUID reservationId = UUID.fromString("50000000-0000-0000-0000-000000001201");
        UUID queueTicketId = UUID.fromString("52000000-0000-0000-0000-000000001201");
        preassignments.assignments.add(new ReservationResourceAssignment(
            reservationId,
            "R-TABLE-1201",
            "arrived",
            3,
            java.time.Instant.parse("2030-06-20T10:00:00Z"),
            java.time.Instant.parse("2030-06-20T11:30:00Z"),
            "王先生",
            "****1201",
            DINING_TABLE_TYPE,
            TABLE_A1_ID.value(),
            "A01",
            queueTicketId,
            8,
            "called"
        ));

        TableResourceListApplicationService service = new TableResourceListApplicationService(
            diningTables,
            new FakeTableGroupRepository(),
            new FakeSeatingRepository(),
            new FakeCleaningRepository(),
            preassignments
        );

        TableResourceListResult result = service.listResources(
            new TableResourceListQuery(SCOPE, null, null, true, new BusinessDate(java.time.LocalDate.of(2030, 6, 20)))
        );

        assertThat(result.success()).isTrue();
        assertThat(result.resources()).extracting(TableResourceItem::code).containsExactly("A01", "A02");
        assertThat(result.resources().get(0).status()).isEqualTo("reserved");
        assertThat(result.resources().get(0).selectable()).isFalse();
        assertThat(result.resources().get(0).selectionDisabledReason()).isEqualTo("reservation_preassigned");
        assertThat(result.resources().get(0).preassignedReservationId()).isEqualTo(reservationId);
        assertThat(result.resources().get(0).preassignedReservationCode()).isEqualTo("R-TABLE-1201");
        assertThat(result.resources().get(0).preassignedCustomerName()).isEqualTo("王先生");
        assertThat(result.resources().get(0).preassignedPhoneMasked()).isEqualTo("****1201");
        assertThat(result.resources().get(0).preassignedReservationStatus()).isEqualTo("arrived");
        assertThat(result.resources().get(0).preassignedPartySize()).isEqualTo(3);
        assertThat(result.resources().get(0).preassignedResourceCode()).isEqualTo("A01");
        assertThat(result.resources().get(0).preassignedQueueTicketId()).isEqualTo(queueTicketId);
        assertThat(result.resources().get(0).preassignedQueueTicketNumber()).isEqualTo(8);
        assertThat(result.resources().get(0).preassignedQueueTicketStatus()).isEqualTo("called");
        assertThat(result.resources().get(1).status()).isEqualTo("available");
    }

    @Test
    void ignoresSeatedPreassignmentWhenReservationNoLongerOccupiesThatResource() {
        FakeDiningTableRepository diningTables = new FakeDiningTableRepository();
        diningTables.rows.add(tableRow(TABLE_A1_ID, "A01", "A01", "A区", 1, 4, "available"));
        FakeReservationPreassignmentRepository preassignments = new FakeReservationPreassignmentRepository();
        UUID reservationId = UUID.fromString("50000000-0000-0000-0000-000000001202");
        preassignments.assignments.add(new ReservationResourceAssignment(
            reservationId,
            "R-TABLE-1202",
            "seated",
            2,
            java.time.Instant.parse("2030-06-20T10:00:00Z"),
            java.time.Instant.parse("2030-06-20T11:30:00Z"),
            "张先生",
            "****1202",
            DINING_TABLE_TYPE,
            TABLE_A1_ID.value(),
            "A01",
            null,
            null,
            null
        ));

        TableResourceListApplicationService service = new TableResourceListApplicationService(
            diningTables,
            new FakeTableGroupRepository(),
            new FakeSeatingRepository(),
            new FakeCleaningRepository(),
            preassignments
        );

        TableResourceListResult result = service.listResources(
            new TableResourceListQuery(SCOPE, null, null, true, new BusinessDate(java.time.LocalDate.of(2030, 6, 20)))
        );

        assertThat(result.success()).isTrue();
        assertThat(result.resources()).hasSize(1);
        assertThat(result.resources().getFirst().status()).isEqualTo("available");
        assertThat(result.resources().getFirst().selectable()).isTrue();
        assertThat(result.resources().getFirst().preassignedReservationId()).isNull();
        assertThat(result.resources().getFirst().preassignedReservationCode()).isNull();
    }

    @Test
    void reservedStatusFilterUsesSelectedBusinessDatePreassignments() {
        FakeDiningTableRepository diningTables = new FakeDiningTableRepository();
        diningTables.rows.add(tableRow(TABLE_A1_ID, "A01", "A01", "A区", 1, 4, "available"));
        diningTables.rows.add(tableRow(TABLE_A2_ID, "A02", "A02", "A区", 2, 6, "available"));
        FakeReservationPreassignmentRepository preassignments = new FakeReservationPreassignmentRepository();
        preassignments.targets.add(new ReservationResourceTarget(DINING_TABLE_TYPE, TABLE_A2_ID.value()));

        TableResourceListApplicationService service = new TableResourceListApplicationService(
            diningTables,
            new FakeTableGroupRepository(),
            new FakeSeatingRepository(),
            new FakeCleaningRepository(),
            preassignments
        );

        TableResourceListResult result = service.listResources(
            new TableResourceListQuery(SCOPE, "reserved", null, true, new BusinessDate(java.time.LocalDate.of(2030, 6, 20)))
        );

        assertThat(result.success()).isTrue();
        assertThat(result.resources()).extracting(TableResourceItem::code).containsExactly("A02");
        assertThat(result.resources().getFirst().status()).isEqualTo("reserved");
    }

    private static DiningTable table(TableId id, String code, int min, int max, DiningTableStatus status) {
        return new DiningTable(id, SCOPE, AREA_ID, code, new CapacityRange(min, max), status, true);
    }

    private static DiningTableResourceRow tableRow(
        TableId id,
        String code,
        String displayName,
        String areaName,
        int min,
        int max,
        String status
    ) {
        return new DiningTableResourceRow(id.value(), code, displayName, areaName, min, max, status);
    }

    private static TableGroup group(TableGroupId id, String code, int min, int max, TableGroupStatus status) {
        return new TableGroup(id, SCOPE, code, "fixed", new CapacityRange(min, max), status);
    }

    private static TableGroupMember member(TableGroupId groupId, TableId tableId) {
        return new TableGroupMember(UUID.randomUUID(), SCOPE, groupId, tableId, "member");
    }

    private static final class FakeDiningTableRepository implements DiningTableRepositoryPort {
        private final List<DiningTable> tables = new ArrayList<>();
        private final List<DiningTableResourceRow> rows = new ArrayList<>();
        private int saveCalls = 0;

        @Override
        public Optional<DiningTable> findById(StoreScope scope, TableId tableId) {
            return tables.stream().filter(table -> table.id().equals(tableId)).findFirst();
        }

        @Override
        public List<DiningTable> findActiveByArea(StoreScope scope, UUID areaId) {
            return tables.stream().filter(table -> table.areaId().equals(areaId)).toList();
        }

        @Override
        public List<DiningTable> findCandidates(StoreScope scope, PartySize partySize, com.rpb.reservation.common.time.BusinessDate businessDate) {
            return tables.stream().filter(table -> table.capacity().includes(partySize)).toList();
        }

        @Override
        public List<DiningTable> findVisibleResources(StoreScope scope, String status, PartySize partySize) {
            return tables.stream()
                .filter(table -> status == null || table.status().code().equals(status))
                .filter(table -> partySize == null || table.capacity().includes(partySize))
                .toList();
        }

        @Override
        public List<DiningTableResourceRow> findVisibleResourceRows(StoreScope scope, String status, PartySize partySize) {
            return rows.stream()
                .filter(row -> status == null || row.status().equals(status))
                .filter(row -> partySize == null || row.capacityMin() <= partySize.value() && row.capacityMax() >= partySize.value())
                .toList();
        }

        @Override
        public DiningTable save(StoreScope scope, DiningTable table) {
            saveCalls++;
            return table;
        }
    }

    private static final class FakeTableGroupRepository implements TableGroupRepositoryPort {
        private final List<TableGroup> groups = new ArrayList<>();
        private final List<TableGroupMember> members = new ArrayList<>();
        private int saveCalls = 0;

        @Override
        public Optional<TableGroup> findById(StoreScope scope, TableGroupId tableGroupId) {
            return groups.stream().filter(group -> group.id().equals(tableGroupId)).findFirst();
        }

        @Override
        public List<TableGroupMember> findActiveMembers(StoreScope scope, TableGroupId tableGroupId) {
            return members.stream().filter(member -> member.tableGroupId().equals(tableGroupId)).toList();
        }

        @Override
        public List<TableGroup> findActiveGroupsForTable(StoreScope scope, TableId tableId) {
            return members.stream()
                .filter(member -> member.tableId().equals(tableId))
                .map(TableGroupMember::tableGroupId)
                .flatMap(groupId -> findById(scope, groupId).stream())
                .toList();
        }

        @Override
        public List<TableGroup> findActiveTemporaryGroupsForTable(StoreScope scope, TableId tableId) {
            return members.stream()
                .filter(member -> member.tableId().equals(tableId))
                .map(TableGroupMember::tableGroupId)
                .flatMap(groupId -> findById(scope, groupId).stream())
                .filter(group -> "temporary".equals(group.groupType()))
                .filter(group -> group.status() == TableGroupStatus.CREATED || group.status() == TableGroupStatus.LOCKED || group.status() == TableGroupStatus.OCCUPIED)
                .toList();
        }

        @Override
        public List<TableGroup> findCandidates(StoreScope scope, PartySize partySize, com.rpb.reservation.common.time.BusinessDate businessDate) {
            return groups.stream().filter(group -> group.capacity().includes(partySize)).toList();
        }

        @Override
        public List<TableGroup> findVisibleGroups(StoreScope scope, String status, PartySize partySize) {
            return groups.stream()
                .filter(group -> status == null || group.status().code().equals(status))
                .filter(group -> partySize == null || group.capacity().includes(partySize))
                .toList();
        }

        @Override
        public TableGroup save(StoreScope scope, TableGroup tableGroup) {
            saveCalls++;
            return tableGroup;
        }

        @Override
        public TableGroupMember saveMember(StoreScope scope, TableGroupMember member) {
            return member;
        }
    }

    private static final class FakeSeatingRepository implements SeatingRepositoryPort {
        private final List<ActiveOccupancy> activeOccupancies = new ArrayList<>();

        @Override
        public Optional<Seating> findById(StoreScope scope, SeatingId seatingId) {
            return activeOccupancies.stream()
                .map(ActiveOccupancy::seating)
                .filter(seating -> seating.id().equals(seatingId))
                .findFirst();
        }

        @Override
        public Optional<Seating> findActiveBySource(StoreScope scope, String sourceType, UUID sourceId) {
            return Optional.empty();
        }

        @Override
        public boolean existsActiveResourceOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
            return findActiveOccupancy(scope, resourceType, resourceId).isPresent();
        }

        @Override
        public Optional<Seating> findActiveOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
            return activeOccupancies.stream()
                .filter(occupancy -> occupancy.resourceType().equals(resourceType))
                .filter(occupancy -> occupancy.resourceId().equals(resourceId))
                .map(ActiveOccupancy::seating)
                .findFirst();
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

    private record ActiveOccupancy(String resourceType, UUID resourceId, Seating seating) {
    }

    private static final class FakeCleaningRepository implements CleaningRepositoryPort {
        private final List<Cleaning> activeCleanings = new ArrayList<>();

        @Override
        public Optional<Cleaning> findById(StoreScope scope, CleaningId cleaningId) {
            return activeCleanings.stream().filter(cleaning -> cleaning.id().equals(cleaningId)).findFirst();
        }

        @Override
        public Optional<Cleaning> findActiveByResource(StoreScope scope, String resourceType, UUID resourceId) {
            return activeCleanings.stream()
                .filter(cleaning -> cleaning.resourceType().equals(resourceType))
                .filter(cleaning -> cleaning.resourceId().equals(resourceId))
                .findFirst();
        }

        @Override
        public Optional<Cleaning> findBySeating(StoreScope scope, SeatingId seatingId) {
            return activeCleanings.stream().filter(cleaning -> cleaning.seatingId().equals(seatingId)).findFirst();
        }

        @Override
        public Cleaning save(StoreScope scope, Cleaning cleaning) {
            return cleaning;
        }
    }

    private static final class FakeReservationPreassignmentRepository implements ReservationPreassignmentRepositoryPort {
        private final Set<ReservationResourceAssignment> assignments = new HashSet<>();
        private final Set<ReservationResourceTarget> targets = new HashSet<>();

        @Override
        public boolean existsActiveResourceConflict(
            StoreScope scope,
            String resourceType,
            UUID resourceId,
            BusinessDate businessDate,
            TimeRange timeRange
        ) {
            return targets.contains(new ReservationResourceTarget(resourceType, resourceId));
        }

        @Override
        public Set<ReservationResourceTarget> findActiveResourceTargetsForDate(StoreScope scope, BusinessDate businessDate) {
            return targets;
        }

        @Override
        public Set<ReservationResourceAssignment> findActiveResourceAssignmentsForDate(StoreScope scope, BusinessDate businessDate) {
            if (!assignments.isEmpty()) {
                return assignments;
            }
            return targets.stream()
                .map(target -> new ReservationResourceAssignment(
                    UUID.randomUUID(),
                    "R-PREASSIGNED",
                    "confirmed",
                    4,
                    null,
                    null,
                    null,
                    null,
                    target.resourceType(),
                    target.resourceId(),
                    null,
                    null,
                    null,
                    null
                ))
                .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public ReservationPreassignment save(StoreScope scope, ReservationPreassignment preassignment) {
            return preassignment;
        }
    }

    private static final class FakeStoreRepository implements StoreRepositoryPort {
        @Override
        public Optional<Store> findById(StoreScope scope) {
            return Optional.of(Store.skeleton(scope.storeId(), scope.tenantId(), "S-1201", "Asia/Singapore", "zh-SG", "operational"));
        }

        @Override
        public Optional<StorePolicy> findCurrentPolicy(StoreScope scope, OffsetDateTime at) {
            return Optional.empty();
        }

        @Override
        public Store save(StoreScope scope, Store store) {
            return store;
        }

        @Override
        public StorePolicy savePolicy(StoreScope scope, StorePolicy policy) {
            return policy;
        }
    }
}
