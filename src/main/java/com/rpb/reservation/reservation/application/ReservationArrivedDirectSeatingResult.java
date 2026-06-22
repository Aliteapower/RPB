package com.rpb.reservation.reservation.application;

import java.util.List;
import java.util.UUID;

public record ReservationArrivedDirectSeatingResult(
    boolean success,
    ReservationArrivedDirectSeatingError error,
    UUID reservationId,
    String reservationCode,
    UUID seatingId,
    String resourceType,
    UUID resourceId,
    int partySizeSnapshot,
    String reservationStatus,
    String seatingStatus,
    String seatingResourceStatus,
    String tableStatus,
    List<String> groupMemberStatuses,
    List<UUID> occupiedTableIds,
    String idempotencyStatus,
    List<String> events,
    List<UUID> businessEventIds,
    List<UUID> stateTransitionLogIds,
    UUID auditLogId,
    boolean alreadySeated,
    boolean replayed,
    boolean retryLater
) {

    public ReservationArrivedDirectSeatingResult {
        groupMemberStatuses = groupMemberStatuses == null ? List.of() : List.copyOf(groupMemberStatuses);
        occupiedTableIds = occupiedTableIds == null ? List.of() : List.copyOf(occupiedTableIds);
        events = events == null ? List.of() : List.copyOf(events);
        businessEventIds = businessEventIds == null ? List.of() : List.copyOf(businessEventIds);
        stateTransitionLogIds = stateTransitionLogIds == null ? List.of() : List.copyOf(stateTransitionLogIds);
    }

    public static ReservationArrivedDirectSeatingResult success(
        UUID reservationId,
        String reservationCode,
        UUID seatingId,
        String resourceType,
        UUID resourceId,
        int partySizeSnapshot,
        String tableStatus,
        List<String> groupMemberStatuses,
        List<UUID> occupiedTableIds,
        String idempotencyStatus,
        List<String> events,
        List<UUID> businessEventIds,
        List<UUID> stateTransitionLogIds,
        UUID auditLogId
    ) {
        return new ReservationArrivedDirectSeatingResult(
            true,
            null,
            reservationId,
            reservationCode,
            seatingId,
            resourceType,
            resourceId,
            partySizeSnapshot,
            "seated",
            "occupied",
            "active",
            tableStatus,
            groupMemberStatuses,
            occupiedTableIds,
            idempotencyStatus,
            events,
            businessEventIds,
            stateTransitionLogIds,
            auditLogId,
            false,
            false,
            false
        );
    }

    public static ReservationArrivedDirectSeatingResult alreadySeated(
        UUID reservationId,
        String reservationCode,
        UUID seatingId,
        String resourceType,
        UUID resourceId,
        int partySizeSnapshot,
        String seatingStatus,
        String seatingResourceStatus,
        String tableStatus,
        List<String> groupMemberStatuses,
        List<UUID> occupiedTableIds,
        String idempotencyStatus
    ) {
        return new ReservationArrivedDirectSeatingResult(
            true,
            null,
            reservationId,
            reservationCode,
            seatingId,
            resourceType,
            resourceId,
            partySizeSnapshot,
            "seated",
            seatingStatus,
            seatingResourceStatus,
            tableStatus,
            groupMemberStatuses,
            occupiedTableIds,
            idempotencyStatus,
            List.of(),
            List.of(),
            List.of(),
            null,
            true,
            false,
            false
        );
    }

    public static ReservationArrivedDirectSeatingResult replay(
        UUID reservationId,
        String reservationCode,
        UUID seatingId,
        String resourceType,
        UUID resourceId,
        int partySizeSnapshot,
        String reservationStatus,
        String seatingStatus,
        String seatingResourceStatus,
        String tableStatus,
        List<String> groupMemberStatuses,
        boolean alreadySeated
    ) {
        return new ReservationArrivedDirectSeatingResult(
            true,
            null,
            reservationId,
            reservationCode,
            seatingId,
            resourceType,
            resourceId,
            partySizeSnapshot,
            reservationStatus,
            seatingStatus,
            seatingResourceStatus,
            tableStatus,
            groupMemberStatuses,
            List.of(),
            "completed",
            List.of(),
            List.of(),
            List.of(),
            null,
            alreadySeated,
            true,
            false
        );
    }

    public static ReservationArrivedDirectSeatingResult failure(ReservationArrivedDirectSeatingError error) {
        return new ReservationArrivedDirectSeatingResult(
            false,
            error,
            null,
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
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            false,
            false
        );
    }

    public static ReservationArrivedDirectSeatingResult retryLater(ReservationArrivedDirectSeatingError error) {
        return new ReservationArrivedDirectSeatingResult(
            false,
            error,
            null,
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
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            false,
            true
        );
    }
}
