package com.rpb.reservation.reservation.command;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.reservation.value.ReservationCode;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain command skeleton for reservation creation intent.
 *
 * <p>This is not an API DTO, request model, mapper, or application service input contract.</p>
 */
public record CreateReservationCommand(
        StoreScope storeScope,
        ReservationCode reservationCode,
        PartySize partySize,
        Optional<IdempotencyKey> idempotencyKey) {

    public CreateReservationCommand(StoreScope storeScope, ReservationCode reservationCode, PartySize partySize) {
        this(storeScope, reservationCode, partySize, Optional.empty());
    }

    public CreateReservationCommand {
        Objects.requireNonNull(storeScope, "storeScope must not be null");
        Objects.requireNonNull(reservationCode, "reservationCode must not be null");
        Objects.requireNonNull(partySize, "partySize must not be null");
        idempotencyKey = idempotencyKey == null ? Optional.empty() : idempotencyKey;
    }

    public String intentCode() {
        return "reservation.create.command";
    }
}
