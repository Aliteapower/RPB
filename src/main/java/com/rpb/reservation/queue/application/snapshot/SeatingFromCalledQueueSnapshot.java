package com.rpb.reservation.queue.application.snapshot;

import java.util.List;
import java.util.UUID;

public record SeatingFromCalledQueueSnapshot(
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
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
    public SeatingFromCalledQueueSnapshot {
        groupMemberStatuses = groupMemberStatuses == null ? List.of() : List.copyOf(groupMemberStatuses);
    }
}
