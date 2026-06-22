package com.rpb.reservation.table.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import java.util.List;
import java.util.Optional;

public interface TableGroupRepositoryPort {

    Optional<TableGroup> findById(StoreScope scope, TableGroupId tableGroupId);

    List<TableGroupMember> findActiveMembers(StoreScope scope, TableGroupId tableGroupId);

    List<TableGroup> findActiveGroupsForTable(StoreScope scope, TableId tableId);

    List<TableGroup> findCandidates(StoreScope scope, PartySize partySize, BusinessDate businessDate);

    TableGroup save(StoreScope scope, TableGroup tableGroup);

    TableGroupMember saveMember(StoreScope scope, TableGroupMember member);
}
