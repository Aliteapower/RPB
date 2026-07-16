package com.rpb.reservation.table.application.service;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.PartySize;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TemporaryTableGroupCommand(
    StoreScope scope,
    List<UUID> tableIds,
    PartySize partySize,
    BusinessDate businessDate,
    UUID sourceReservationId
) {

    public TemporaryTableGroupCommand {
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(partySize, "party_size_required");
        Objects.requireNonNull(businessDate, "business_date_required");
        tableIds = tableIds == null ? List.of() : List.copyOf(tableIds);
    }
}
