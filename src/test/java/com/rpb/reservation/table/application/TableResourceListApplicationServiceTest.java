package com.rpb.reservation.table.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.application.service.TableResourceListApplicationService;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.status.TableGroupStatus;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TableResourceListApplicationServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000001201");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000001201");
    private static final UUID AREA_ID = UUID.fromString("60000000-0000-0000-0000-000000001201");
    private static final TableId TABLE_A1_ID = new TableId(UUID.fromString("70000000-0000-0000-0000-000000001201"));
    private static final TableId TABLE_A2_ID = new TableId(UUID.fromString("70000000-0000-0000-0000-000000001202"));
    private static final TableGroupId GROUP_VIP_ID = new TableGroupId(UUID.fromString("71000000-0000-0000-0000-000000001201"));
    private static final StoreScope SCOPE = new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));

    @Test
    void listsConfiguredTableNumbersAndGroupsForSelection() {
        FakeDiningTableRepository diningTables = new FakeDiningTableRepository();
        diningTables.tables.add(table(TABLE_A1_ID, "A01", 1, 4, DiningTableStatus.AVAILABLE));
        diningTables.tables.add(table(TABLE_A2_ID, "A02", 2, 6, DiningTableStatus.OCCUPIED));
        FakeTableGroupRepository tableGroups = new FakeTableGroupRepository();
        tableGroups.groups.add(group(GROUP_VIP_ID, "VIP-1", 8, 12, TableGroupStatus.ACTIVE));
        tableGroups.members.add(member(GROUP_VIP_ID, TABLE_A1_ID));
        tableGroups.members.add(member(GROUP_VIP_ID, TABLE_A2_ID));

        TableResourceListApplicationService service = new TableResourceListApplicationService(diningTables, tableGroups);

        TableResourceListResult result = service.listResources(new TableResourceListQuery(SCOPE, null, null, true));

        assertThat(result.success()).isTrue();
        assertThat(result.resources()).extracting(TableResourceItem::code)
            .containsExactly("A01", "A02", "VIP-1");
        assertThat(result.resources().get(0).resourceType()).isEqualTo("dining_table");
        assertThat(result.resources().get(0).selectable()).isTrue();
        assertThat(result.resources().get(1).selectable()).isFalse();
        assertThat(result.resources().get(1).selectionDisabledReason()).isEqualTo("status_unavailable");
        assertThat(result.resources().get(2).resourceType()).isEqualTo("table_group");
        assertThat(result.resources().get(2).memberTableCodes()).containsExactly("A01", "A02");
    }

    @Test
    void filtersByStatusAndPartySizeWithoutMutatingResources() {
        FakeDiningTableRepository diningTables = new FakeDiningTableRepository();
        diningTables.tables.add(table(TABLE_A1_ID, "A01", 1, 4, DiningTableStatus.AVAILABLE));
        diningTables.tables.add(table(TABLE_A2_ID, "A02", 2, 6, DiningTableStatus.OCCUPIED));
        FakeTableGroupRepository tableGroups = new FakeTableGroupRepository();
        tableGroups.groups.add(group(GROUP_VIP_ID, "VIP-1", 8, 12, TableGroupStatus.ACTIVE));

        TableResourceListApplicationService service = new TableResourceListApplicationService(diningTables, tableGroups);

        TableResourceListResult result = service.listResources(new TableResourceListQuery(SCOPE, "available", 4, true));

        assertThat(result.success()).isTrue();
        assertThat(result.resources()).extracting(TableResourceItem::code).containsExactly("A01");
        assertThat(diningTables.saveCalls).isZero();
        assertThat(tableGroups.saveCalls).isZero();
    }

    @Test
    void returnsEmptyListWhenBackendTableSetupHasNoTablesOrGroups() {
        TableResourceListApplicationService service = new TableResourceListApplicationService(
            new FakeDiningTableRepository(),
            new FakeTableGroupRepository()
        );

        TableResourceListResult result = service.listResources(new TableResourceListQuery(SCOPE, null, null, true));

        assertThat(result.success()).isTrue();
        assertThat(result.resources()).isEmpty();
    }

    private static DiningTable table(TableId id, String code, int min, int max, DiningTableStatus status) {
        return new DiningTable(id, SCOPE, AREA_ID, code, new CapacityRange(min, max), status, true);
    }

    private static TableGroup group(TableGroupId id, String code, int min, int max, TableGroupStatus status) {
        return new TableGroup(id, SCOPE, code, "fixed", new CapacityRange(min, max), status);
    }

    private static TableGroupMember member(TableGroupId groupId, TableId tableId) {
        return new TableGroupMember(UUID.randomUUID(), SCOPE, groupId, tableId, "member");
    }

    private static final class FakeDiningTableRepository implements DiningTableRepositoryPort {
        private final List<DiningTable> tables = new ArrayList<>();
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
}
