package com.rpb.reservation.reservation.application.snapshot;

import java.util.List;
import java.util.UUID;

public record ReservationArrivedDirectSeatingSnapshot(
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    UUID seatingId,
    String resourceType,
    UUID resourceId,
    int partySizeSnapshot,
    String seatingStatus,
    String seatingResourceStatus,
    String tableStatus,
    List<String> groupMemberStatuses,
    boolean alreadySeated
) {
    public ReservationArrivedDirectSeatingSnapshot {
        groupMemberStatuses = groupMemberStatuses == null ? List.of() : List.copyOf(groupMemberStatuses);
    }
}
