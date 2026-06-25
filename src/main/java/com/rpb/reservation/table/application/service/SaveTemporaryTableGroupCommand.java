package com.rpb.reservation.table.application.service;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SaveTemporaryTableGroupCommand(
    StoreScope scope,
    String groupName,
    List<UUID> tableIds,
    BusinessDate businessDate
) {

    public SaveTemporaryTableGroupCommand {
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(businessDate, "business_date_required");
        groupName = groupName == null ? null : groupName.trim();
        tableIds = tableIds == null ? List.of() : List.copyOf(tableIds);
    }
}
