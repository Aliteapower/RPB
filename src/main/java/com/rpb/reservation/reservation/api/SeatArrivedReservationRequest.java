package com.rpb.reservation.reservation.api;

import java.util.Optional;
import java.util.UUID;

public record SeatArrivedReservationRequest(
    UUID tableId,
    UUID tableGroupId,
    String overrideReasonCode,
    String overrideNote,
    String note
) {

    public Optional<ReservationApiErrorCode> validateContract() {
        if (tableId != null && tableGroupId != null) {
            return Optional.of(ReservationApiErrorCode.RESOURCE_SELECTION_CONFLICT);
        }
        if (tableId == null && tableGroupId == null) {
            return Optional.of(ReservationApiErrorCode.RESOURCE_SELECTION_REQUIRED);
        }
        return Optional.empty();
    }
}
