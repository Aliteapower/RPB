package com.rpb.reservation.walkin.domain;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.walkin.value.WalkInId;
import java.util.Objects;

/**
 * WalkIn domain skeleton. WalkIn can seat directly and is not QueueTicket.
 */
public record WalkIn(WalkInId id, StoreScope scope, PartySize partySize, String status) {

    public WalkIn {
        Objects.requireNonNull(id, "walk_in_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(partySize, "party_size_required");
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("walk_in_status_required");
        }
    }

    public String directSeatingIntent() {
        return "walk_in.direct_seating.intent";
    }

    public String domainBoundary() {
        return "WalkIn is an arrival scenario and is not QueueTicket.";
    }
}
