package com.rpb.reservation.turnover.domain;

import com.rpb.reservation.cleaning.value.CleaningId;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.turnover.status.TurnoverStatus;
import com.rpb.reservation.turnover.value.TurnoverId;
import java.util.Objects;

/**
 * Turnover domain skeleton. Turnover is a result/metric from Seating +
 * Cleaning, never live action by itself.
 */
public record Turnover(
    TurnoverId id,
    StoreScope scope,
    SeatingId seatingId,
    CleaningId cleaningId,
    BusinessDate businessDate,
    TurnoverStatus status
) {

    public Turnover {
        Objects.requireNonNull(id, "turnover_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(seatingId, "seating_id_required");
        Objects.requireNonNull(businessDate, "business_date_required");
        Objects.requireNonNull(status, "turnover_status_required");
    }

    public String recordIntent() {
        return "turnover.record.intent";
    }

    public String domainBoundary() {
        return "Turnover is derived from Seating and Cleaning, not Reservation alone.";
    }
}
