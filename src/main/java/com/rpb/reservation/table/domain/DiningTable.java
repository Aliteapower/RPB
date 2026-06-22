package com.rpb.reservation.table.domain;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.value.TableId;
import java.util.Objects;
import java.util.UUID;

/**
 * DiningTable domain skeleton. A table is a base resource, not a TableGroup.
 */
public record DiningTable(
    TableId id,
    StoreScope scope,
    UUID areaId,
    String tableCode,
    CapacityRange capacity,
    DiningTableStatus status,
    boolean combinable
) {

    public DiningTable {
        Objects.requireNonNull(id, "table_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(areaId, "area_id_required");
        Objects.requireNonNull(capacity, "capacity_range_required");
        Objects.requireNonNull(status, "table_status_required");
        if (tableCode == null || tableCode.isBlank()) {
            throw new IllegalArgumentException("table_code_required");
        }
    }

    public String lockIntent() {
        return "dining_table.lock.intent";
    }

    public String domainBoundary() {
        return "DiningTable is a base resource and is not TableGroup.";
    }
}
