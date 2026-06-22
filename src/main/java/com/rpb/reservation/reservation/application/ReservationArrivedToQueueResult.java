package com.rpb.reservation.reservation.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReservationArrivedToQueueResult(
    boolean success,
    ReservationArrivedToQueueError error,
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
    UUID queueGroupId,
    String queueGroupCode,
    int partySize,
    String partySizeGroup,
    LocalDate businessDate,
    int queuePosition,
    String idempotencyStatus,
    List<String> events,
    List<UUID> businessEventIds,
    List<UUID> stateTransitionLogIds,
    UUID auditLogId,
    boolean alreadyQueued,
    boolean replayed,
    boolean retryLater
) {

    public ReservationArrivedToQueueResult {
        events = events == null ? List.of() : List.copyOf(events);
        businessEventIds = businessEventIds == null ? List.of() : List.copyOf(businessEventIds);
        stateTransitionLogIds = stateTransitionLogIds == null ? List.of() : List.copyOf(stateTransitionLogIds);
    }

    public static ReservationArrivedToQueueResult success(
        UUID reservationId,
        String reservationCode,
        UUID queueTicketId,
        int queueTicketNumber,
        UUID queueGroupId,
        String queueGroupCode,
        int partySize,
        String partySizeGroup,
        LocalDate businessDate,
        int queuePosition,
        String idempotencyStatus,
        List<String> events,
        List<UUID> businessEventIds,
        List<UUID> stateTransitionLogIds,
        UUID auditLogId
    ) {
        return new ReservationArrivedToQueueResult(
            true,
            null,
            reservationId,
            reservationCode,
            "arrived",
            queueTicketId,
            queueTicketNumber,
            "waiting",
            queueGroupId,
            queueGroupCode,
            partySize,
            partySizeGroup,
            businessDate,
            queuePosition,
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

    public static ReservationArrivedToQueueResult alreadyQueued(
        UUID reservationId,
        String reservationCode,
        UUID queueTicketId,
        int queueTicketNumber,
        UUID queueGroupId,
        String queueGroupCode,
        int partySize,
        String partySizeGroup,
        LocalDate businessDate,
        int queuePosition,
        String idempotencyStatus
    ) {
        return new ReservationArrivedToQueueResult(
            true,
            null,
            reservationId,
            reservationCode,
            "arrived",
            queueTicketId,
            queueTicketNumber,
            "waiting",
            queueGroupId,
            queueGroupCode,
            partySize,
            partySizeGroup,
            businessDate,
            queuePosition,
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

    public static ReservationArrivedToQueueResult replay(
        UUID reservationId,
        String reservationCode,
        UUID queueTicketId,
        int queueTicketNumber,
        String queueTicketStatus,
        UUID queueGroupId,
        String queueGroupCode,
        int partySize,
        String partySizeGroup,
        LocalDate businessDate,
        int queuePosition,
        boolean alreadyQueued
    ) {
        return new ReservationArrivedToQueueResult(
            true,
            null,
            reservationId,
            reservationCode,
            "arrived",
            queueTicketId,
            queueTicketNumber,
            queueTicketStatus,
            queueGroupId,
            queueGroupCode,
            partySize,
            partySizeGroup,
            businessDate,
            queuePosition,
            "completed",
            List.of(),
            List.of(),
            List.of(),
            null,
            alreadyQueued,
            true,
            false
        );
    }

    public static ReservationArrivedToQueueResult failure(ReservationArrivedToQueueError error) {
        return new ReservationArrivedToQueueResult(
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
            0,
            null,
            null,
            0,
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

    public static ReservationArrivedToQueueResult retryLater(ReservationArrivedToQueueError error) {
        return new ReservationArrivedToQueueResult(
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
            0,
            null,
            null,
            0,
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
