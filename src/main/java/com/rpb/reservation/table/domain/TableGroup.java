package com.rpb.reservation.table.domain;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.table.status.TableGroupStatus;
import com.rpb.reservation.table.value.TableGroupId;
import java.util.Objects;

/**
 * TableGroup domain skeleton. It models fixed or temporary combined resources
 * without replacing member DiningTables.
 */
public record TableGroup(
    TableGroupId id,
    StoreScope scope,
    String groupCode,
    String groupType,
    CapacityRange capacity,
    TableGroupStatus status
) {

    public TableGroup {
        Objects.requireNonNull(id, "table_group_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(capacity, "capacity_range_required");
        Objects.requireNonNull(status, "table_group_status_required");
        requireText(groupCode, "table_group_code_required");
        requireText(groupType, "table_group_type_required");
    }

    public String releaseIntent() {
        return "table_group.release.intent";
    }

    public String domainBoundary() {
        return "TableGroup is a combined resource and does not replace DiningTable.";
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
