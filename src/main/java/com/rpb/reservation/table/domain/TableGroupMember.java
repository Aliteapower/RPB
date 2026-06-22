package com.rpb.reservation.table.domain;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import java.util.Objects;
import java.util.UUID;

/**
 * TableGroupMember domain skeleton. It links TableGroup to DiningTable and
 * cannot reference another TableGroup.
 */
public record TableGroupMember(UUID id, StoreScope scope, TableGroupId tableGroupId, TableId tableId, String memberRole) {

    public TableGroupMember {
        Objects.requireNonNull(id, "table_group_member_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(tableGroupId, "table_group_id_required");
        Objects.requireNonNull(tableId, "table_id_required");
    }

    public String status() {
        return "active_member_skeleton";
    }

    public String removeIntent() {
        return "table_group_member.remove.intent";
    }

    public String domainBoundary() {
        return "TableGroupMember connects a group to a table, never group to group.";
    }
}
