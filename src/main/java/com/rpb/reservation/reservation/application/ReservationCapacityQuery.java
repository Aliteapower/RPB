package com.rpb.reservation.reservation.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.common.value.PartySize;
import java.util.Locale;
import java.util.Objects;

public record ReservationCapacityQuery(
    StoreScope scope,
    String source,
    String periodKey,
    BusinessDate businessDate,
    TimeRange timeRange,
    PartySize partySize,
    int currentUsage
) {

    public ReservationCapacityQuery {
        Objects.requireNonNull(scope, "scope_required");
        Objects.requireNonNull(businessDate, "business_date_required");
        Objects.requireNonNull(timeRange, "time_range_required");
        Objects.requireNonNull(partySize, "party_size_required");
        source = source == null ? "staff" : source.trim().toLowerCase(Locale.ROOT);
        periodKey = periodKey == null ? "" : periodKey.trim();
        if (currentUsage < 0) {
            throw new IllegalArgumentException("current_usage_must_not_be_negative");
        }
    }
}
