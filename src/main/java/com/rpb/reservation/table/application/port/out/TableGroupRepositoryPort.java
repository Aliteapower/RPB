package com.rpb.reservation.table.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TableGroupRepositoryPort {

    Optional<TableGroup> findById(StoreScope scope, TableGroupId tableGroupId);

    List<TableGroupMember> findActiveMembers(StoreScope scope, TableGroupId tableGroupId);

    List<TableGroup> findActiveGroupsForTable(StoreScope scope, TableId tableId);

    default List<TableGroup> findActiveTemporaryGroupsForTable(StoreScope scope, TableId tableId) {
        throw new UnsupportedOperationException("find_active_temporary_groups_for_table_not_implemented");
    }

    default List<TableGroup> findActiveTemporaryGroupsForTable(
        StoreScope scope,
        TableId tableId,
        OffsetDateTime businessStartAt,
        OffsetDateTime businessEndAt
    ) {
        return findActiveTemporaryGroupsForTable(scope, tableId);
    }

    List<TableGroup> findCandidates(StoreScope scope, PartySize partySize, BusinessDate businessDate);

    default List<TableGroup> findVisibleGroups(StoreScope scope, String status, PartySize partySize) {
        throw new UnsupportedOperationException("find_visible_table_groups_not_implemented");
    }

    default List<TableGroup> findVisibleGroups(
        StoreScope scope,
        String status,
        PartySize partySize,
        OffsetDateTime businessStartAt,
        OffsetDateTime businessEndAt
    ) {
        return findVisibleGroups(scope, status, partySize);
    }

    TableGroup save(StoreScope scope, TableGroup tableGroup);

    TableGroupMember saveMember(StoreScope scope, TableGroupMember member);

    default boolean existsActiveGroupCode(StoreScope scope, String groupCode) {
        throw new UnsupportedOperationException("exists_active_table_group_code_not_implemented");
    }

    default void softDeleteGroupAndMembers(StoreScope scope, TableGroupId tableGroupId, OffsetDateTime deletedAt) {
        throw new UnsupportedOperationException("soft_delete_table_group_and_members_not_implemented");
    }
}
