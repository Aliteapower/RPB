package com.rpb.reservation.table.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.persistence.adapter.DiningTablePersistenceAdapter;
import com.rpb.reservation.table.persistence.adapter.TableGroupPersistenceAdapter;
import com.rpb.reservation.table.persistence.entity.DiningTableEntity;
import com.rpb.reservation.table.persistence.entity.TableGroupEntity;
import com.rpb.reservation.table.persistence.mapper.DefaultDiningTableMapper;
import com.rpb.reservation.table.persistence.mapper.DefaultTableGroupMapper;
import com.rpb.reservation.table.persistence.repository.DiningTableJpaRepository;
import com.rpb.reservation.table.persistence.repository.TableGroupJpaRepository;
import com.rpb.reservation.table.persistence.repository.TableGroupMemberJpaRepository;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TableResourceListPersistenceAdapterTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000001203");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000001203");
    private static final UUID AREA_ID = UUID.fromString("60000000-0000-0000-0000-000000001203");
    private static final UUID TABLE_ID = UUID.fromString("70000000-0000-0000-0000-000000001203");
    private static final UUID GROUP_ID = UUID.fromString("71000000-0000-0000-0000-000000001203");
    private static final StoreScope SCOPE = new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));

    @Test
    void diningTableAdapterQueriesVisibleResourcesWithinTenantAndStoreScope() {
        DiningTableJpaRepository repository = mock(DiningTableJpaRepository.class);
        DiningTableEntity entity = diningTableEntity("A01", "available", 1, 4);
        when(repository.findVisibleResources(TENANT_ID, STORE_ID, "available", 4)).thenReturn(List.of(entity));
        DiningTablePersistenceAdapter adapter = new DiningTablePersistenceAdapter(repository, new DefaultDiningTableMapper());

        List<DiningTable> resources = adapter.findVisibleResources(SCOPE, "available", new PartySize(4));

        assertThat(resources).extracting(DiningTable::tableCode).containsExactly("A01");
        verify(repository).findVisibleResources(TENANT_ID, STORE_ID, "available", 4);
    }

    @Test
    void tableGroupAdapterQueriesVisibleGroupsWithinTenantAndStoreScope() {
        TableGroupJpaRepository groupRepository = mock(TableGroupJpaRepository.class);
        TableGroupMemberJpaRepository memberRepository = mock(TableGroupMemberJpaRepository.class);
        TableGroupEntity entity = tableGroupEntity("VIP-1", "active", 8, 12);
        when(groupRepository.findVisibleGroups(TENANT_ID, STORE_ID, "active", 10)).thenReturn(List.of(entity));
        TableGroupPersistenceAdapter adapter = new TableGroupPersistenceAdapter(
            groupRepository,
            memberRepository,
            new DefaultTableGroupMapper()
        );

        List<TableGroup> resources = adapter.findVisibleGroups(SCOPE, "active", new PartySize(10));

        assertThat(resources).extracting(TableGroup::groupCode).containsExactly("VIP-1");
        verify(groupRepository).findVisibleGroups(TENANT_ID, STORE_ID, "active", 10);
    }

    private static DiningTableEntity diningTableEntity(String code, String status, int min, int max) {
        OffsetDateTime now = OffsetDateTime.parse("2030-06-20T03:00:00Z");
        return DiningTableEntity.of(
            TABLE_ID,
            TENANT_ID,
            STORE_ID,
            AREA_ID,
            code,
            code,
            min,
            max,
            status,
            true,
            now,
            now,
            null,
            0
        );
    }

    private static TableGroupEntity tableGroupEntity(String code, String status, int min, int max) {
        OffsetDateTime now = OffsetDateTime.parse("2030-06-20T03:00:00Z");
        return TableGroupEntity.of(
            GROUP_ID,
            TENANT_ID,
            STORE_ID,
            code,
            "fixed",
            status,
            code,
            min,
            max,
            null,
            null,
            now,
            now,
            null,
            0
        );
    }
}
