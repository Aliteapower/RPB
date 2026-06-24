package com.rpb.reservation.reservation.api;

import java.util.Optional;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record SeatArrivedReservationRequest(
    UUID tableId,
    UUID tableGroupId,
    List<UUID> temporaryTableIds,
    String overrideReasonCode,
    String overrideNote,
    String note
) {

    public SeatArrivedReservationRequest {
        temporaryTableIds = temporaryTableIds == null ? null : List.copyOf(temporaryTableIds);
    }

    public Optional<ReservationApiErrorCode> validateContract() {
        boolean temporarySelectionProvided = temporaryTableIds != null;
        int selectedTargets = (tableId == null ? 0 : 1)
            + (tableGroupId == null ? 0 : 1)
            + (temporarySelectionProvided ? 1 : 0);
        if (selectedTargets > 1) {
            return Optional.of(ReservationApiErrorCode.RESOURCE_SELECTION_CONFLICT);
        }
        if (selectedTargets == 0) {
            return Optional.of(ReservationApiErrorCode.RESOURCE_SELECTION_REQUIRED);
        }
        if (temporarySelectionProvided && temporaryTableIds.size() < 2) {
            return Optional.of(ReservationApiErrorCode.TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED);
        }
        if (temporarySelectionProvided && new HashSet<>(temporaryTableIds).size() != temporaryTableIds.size()) {
            return Optional.of(ReservationApiErrorCode.TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE);
        }
        return Optional.empty();
    }
}
