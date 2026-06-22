package com.rpb.reservation.queue.api;

import java.util.Optional;
import java.util.UUID;

public record SeatCalledQueueTicketRequest(
    UUID tableId,
    UUID tableGroupId,
    String overrideReasonCode,
    String overrideNote,
    String note
) {

    public Optional<SeatingFromCalledQueueApiErrorCode> validateContract() {
        if (tableId != null && tableGroupId != null) {
            return Optional.of(SeatingFromCalledQueueApiErrorCode.RESOURCE_SELECTION_CONFLICT);
        }
        if (tableId == null && tableGroupId == null) {
            return Optional.of(SeatingFromCalledQueueApiErrorCode.RESOURCE_SELECTION_REQUIRED);
        }
        return Optional.empty();
    }
}
