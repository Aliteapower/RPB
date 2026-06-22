package com.rpb.reservation.seating.command;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.seating.value.SeatingId;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain command skeleton for seating intent.
 *
 * <p>This command does not assign tables or implement seating workflow orchestration.</p>
 */
public record SeatPartyCommand(
        StoreScope storeScope,
        SeatingId seatingId,
        PartySize partySizeSnapshot,
        Optional<IdempotencyKey> idempotencyKey) {

    public SeatPartyCommand {
        Objects.requireNonNull(storeScope, "storeScope must not be null");
        Objects.requireNonNull(seatingId, "seatingId must not be null");
        Objects.requireNonNull(partySizeSnapshot, "partySizeSnapshot must not be null");
        idempotencyKey = idempotencyKey == null ? Optional.empty() : idempotencyKey;
    }

    public String intentCode() {
        return "seating.seat_party.command";
    }
}
