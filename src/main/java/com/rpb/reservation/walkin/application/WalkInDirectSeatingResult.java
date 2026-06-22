package com.rpb.reservation.walkin.application;

import java.util.List;
import java.util.UUID;

public record WalkInDirectSeatingResult(
    boolean success,
    WalkInDirectSeatingError error,
    UUID walkInId,
    UUID seatingId,
    String resourceType,
    UUID resourceId,
    int partySizeSnapshot,
    String walkInStatus,
    String seatingStatus,
    String resourceStatus,
    String idempotencyStatus,
    List<UUID> businessEventIds,
    List<UUID> stateTransitionLogIds,
    UUID auditLogId,
    boolean replayed,
    boolean retryLater
) {

    public WalkInDirectSeatingResult {
        businessEventIds = businessEventIds == null ? List.of() : List.copyOf(businessEventIds);
        stateTransitionLogIds = stateTransitionLogIds == null ? List.of() : List.copyOf(stateTransitionLogIds);
    }

    public static WalkInDirectSeatingResult success(
        UUID walkInId,
        UUID seatingId,
        String resourceType,
        UUID resourceId,
        int partySizeSnapshot,
        String walkInStatus,
        String seatingStatus,
        String resourceStatus,
        String idempotencyStatus,
        List<UUID> businessEventIds,
        List<UUID> stateTransitionLogIds,
        UUID auditLogId
    ) {
        return new WalkInDirectSeatingResult(
            true,
            null,
            walkInId,
            seatingId,
            resourceType,
            resourceId,
            partySizeSnapshot,
            walkInStatus,
            seatingStatus,
            resourceStatus,
            idempotencyStatus,
            businessEventIds,
            stateTransitionLogIds,
            auditLogId,
            false,
            false
        );
    }

    public static WalkInDirectSeatingResult replay(
        UUID walkInId,
        UUID seatingId,
        String resourceType,
        UUID resourceId,
        int partySizeSnapshot
    ) {
        return new WalkInDirectSeatingResult(
            true,
            null,
            walkInId,
            seatingId,
            resourceType,
            resourceId,
            partySizeSnapshot,
            "seated",
            "occupied",
            "active",
            "completed",
            List.of(),
            List.of(),
            null,
            true,
            false
        );
    }

    public static WalkInDirectSeatingResult failure(WalkInDirectSeatingError error) {
        return new WalkInDirectSeatingResult(
            false,
            error,
            null,
            null,
            null,
            null,
            0,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            null,
            false,
            false
        );
    }

    public static WalkInDirectSeatingResult retryLater(WalkInDirectSeatingError error) {
        return new WalkInDirectSeatingResult(
            false,
            error,
            null,
            null,
            null,
            null,
            0,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            null,
            false,
            true
        );
    }
}
