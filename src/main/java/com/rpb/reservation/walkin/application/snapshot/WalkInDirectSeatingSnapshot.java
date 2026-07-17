package com.rpb.reservation.walkin.application.snapshot;

import java.util.UUID;

public record WalkInDirectSeatingSnapshot(
    UUID walkInId,
    UUID seatingId,
    String resourceType,
    UUID resourceId,
    int partySizeSnapshot
) {
}
