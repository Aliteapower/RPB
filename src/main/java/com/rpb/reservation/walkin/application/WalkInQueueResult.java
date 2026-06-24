package com.rpb.reservation.walkin.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WalkInQueueResult(
    boolean success,
    WalkInQueueError error,
    UUID walkInId,
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
    int partySize,
    String partySizeGroup,
    LocalDate businessDate,
    Integer queuePosition,
    boolean alreadyQueued,
    String idempotencyStatus,
    List<String> events,
    List<UUID> businessEventIds,
    List<UUID> stateTransitionLogIds,
    UUID auditLogId,
    boolean replayed,
    boolean retryLater
) {

    public WalkInQueueResult {
        events = events == null ? List.of() : List.copyOf(events);
        businessEventIds = businessEventIds == null ? List.of() : List.copyOf(businessEventIds);
        stateTransitionLogIds = stateTransitionLogIds == null ? List.of() : List.copyOf(stateTransitionLogIds);
    }

    public static WalkInQueueResult success(
        UUID walkInId,
        UUID queueTicketId,
        int queueTicketNumber,
        int partySize,
        String partySizeGroup,
        LocalDate businessDate,
        Integer queuePosition,
        String idempotencyStatus,
        List<String> events,
        List<UUID> businessEventIds,
        List<UUID> stateTransitionLogIds,
        UUID auditLogId
    ) {
        return new WalkInQueueResult(
            true,
            null,
            walkInId,
            queueTicketId,
            queueTicketNumber,
            "waiting",
            partySize,
            partySizeGroup,
            businessDate,
            queuePosition,
            false,
            idempotencyStatus,
            events,
            businessEventIds,
            stateTransitionLogIds,
            auditLogId,
            false,
            false
        );
    }

    public static WalkInQueueResult replay(
        UUID walkInId,
        UUID queueTicketId,
        int queueTicketNumber,
        int partySize,
        String partySizeGroup,
        LocalDate businessDate,
        Integer queuePosition
    ) {
        return new WalkInQueueResult(
            true,
            null,
            walkInId,
            queueTicketId,
            queueTicketNumber,
            "waiting",
            partySize,
            partySizeGroup,
            businessDate,
            queuePosition,
            false,
            "completed",
            List.of(),
            List.of(),
            List.of(),
            null,
            true,
            false
        );
    }

    public static WalkInQueueResult failure(WalkInQueueError error) {
        return new WalkInQueueResult(
            false,
            error,
            null,
            null,
            0,
            null,
            0,
            null,
            null,
            null,
            false,
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            false
        );
    }

    public static WalkInQueueResult retryLater(WalkInQueueError error) {
        return new WalkInQueueResult(
            false,
            error,
            null,
            null,
            0,
            null,
            0,
            null,
            null,
            null,
            false,
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            true
        );
    }
}
