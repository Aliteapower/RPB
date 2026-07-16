package com.rpb.reservation.table.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import java.util.Objects;

public record TableResourceListQuery(
    StoreScope scope,
    String status,
    Integer partySize,
    boolean includeGroups,
    BusinessDate businessDate
) {

    public TableResourceListQuery {
        Objects.requireNonNull(scope, "store_scope_required");
        status = normalize(status);
    }

    public TableResourceListQuery(
        StoreScope scope,
        String status,
        Integer partySize,
        boolean includeGroups
    ) {
        this(scope, status, partySize, includeGroups, null);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toLowerCase();
    }
}
