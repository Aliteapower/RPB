package com.rpb.reservation.table.application.service;

import com.rpb.reservation.common.scope.StoreScope;
import java.util.Objects;
import java.util.UUID;

public record DissolveTemporaryTableGroupCommand(
    StoreScope scope,
    UUID tableGroupId
) {

    public DissolveTemporaryTableGroupCommand {
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(tableGroupId, "table_group_id_required");
    }
}
