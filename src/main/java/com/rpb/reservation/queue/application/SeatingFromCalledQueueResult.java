package com.rpb.reservation.queue.application;

import java.util.List;
import java.util.UUID;

public record SeatingFromCalledQueueResult(
    boolean success,
    SeatingFromCalledQueueError error,
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

    public SeatingFromCalledQueueResult {
        groupMemberStatuses = groupMemberStatuses == null ? List.of() : List.copyOf(groupMemberStatuses);
        occupiedTableIds = occupiedTableIds == null ? List.of() : List.copyOf(occupiedTableIds);
        events = events == null ? List.of() : List.copyOf(events);
        businessEventIds = businessEventIds == null ? List.of() : List.copyOf(businessEventIds);
        stateTransitionLogIds = stateTransitionLogIds == null ? List.of() : List.copyOf(stateTransitionLogIds);
    }

    public static SeatingFromCalledQueueResult success(
        UUID queueTicketId,
        int queueTicketNumber,
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
        return new SeatingFromCalledQueueResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            "seated",
            reservationId,
            reservationCode,
            "seated",
            seatingId,
            resourceType,
            resourceId,
            partySizeSnapshot,
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

    public static SeatingFromCalledQueueResult alreadySeated(
        UUID queueTicketId,
        int queueTicketNumber,
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
        return new SeatingFromCalledQueueResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            "seated",
            reservationId,
            reservationCode,
            "seated",
            seatingId,
            resourceType,
            resourceId,
            partySizeSnapshot,
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

    public static SeatingFromCalledQueueResult replay(
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
        return new SeatingFromCalledQueueResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            queueTicketStatus,
            reservationId,
            reservationCode,
            reservationStatus,
            seatingId,
            resourceType,
            resourceId,
            partySizeSnapshot,
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

    public static SeatingFromCalledQueueResult failure(SeatingFromCalledQueueError error) {
        return new SeatingFromCalledQueueResult(
            false,
            error,
            null,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
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

    public static SeatingFromCalledQueueResult retryLater(SeatingFromCalledQueueError error) {
        return new SeatingFromCalledQueueResult(
            false,
            error,
            null,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
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
