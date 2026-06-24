package com.rpb.reservation.queue.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QueueCancelResult(
    boolean success,
    QueueCancelError error,
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    UUID walkInId,
    Instant cancelledAt,
    String cancellationReasonCode,
    boolean alreadyCancelled,
    String idempotencyStatus,
    List<String> events,
    List<UUID> businessEventIds,
    List<UUID> stateTransitionLogIds,
    UUID auditLogId,
    boolean replayed,
    boolean retryLater
) {

    public QueueCancelResult {
        events = events == null ? List.of() : List.copyOf(events);
        businessEventIds = businessEventIds == null ? List.of() : List.copyOf(businessEventIds);
        stateTransitionLogIds = stateTransitionLogIds == null ? List.of() : List.copyOf(stateTransitionLogIds);
    }

    public static QueueCancelResult success(
        UUID queueTicketId,
        int queueTicketNumber,
        UUID reservationId,
        String reservationCode,
        String reservationStatus,
        UUID walkInId,
        Instant cancelledAt,
        String cancellationReasonCode,
        String idempotencyStatus,
        List<String> events,
        List<UUID> businessEventIds,
        List<UUID> stateTransitionLogIds,
        UUID auditLogId
    ) {
        return new QueueCancelResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            "cancelled",
            reservationId,
            reservationCode,
            reservationStatus,
            walkInId,
            cancelledAt,
            cancellationReasonCode,
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

    public static QueueCancelResult alreadyCancelled(
        UUID queueTicketId,
        int queueTicketNumber,
        UUID reservationId,
        String reservationCode,
        String reservationStatus,
        UUID walkInId,
        Instant cancelledAt,
        String cancellationReasonCode,
        String idempotencyStatus
    ) {
        return new QueueCancelResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            "cancelled",
            reservationId,
            reservationCode,
            reservationStatus,
            walkInId,
            cancelledAt,
            cancellationReasonCode,
            true,
            idempotencyStatus,
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            false
        );
    }

    public static QueueCancelResult replay(
        UUID queueTicketId,
        int queueTicketNumber,
        UUID reservationId,
        String reservationCode,
        String reservationStatus,
        UUID walkInId,
        Instant cancelledAt,
        String cancellationReasonCode,
        boolean alreadyCancelled
    ) {
        return new QueueCancelResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            "cancelled",
            reservationId,
            reservationCode,
            reservationStatus,
            walkInId,
            cancelledAt,
            cancellationReasonCode,
            alreadyCancelled,
            "completed",
            List.of(),
            List.of(),
            List.of(),
            null,
            true,
            false
        );
    }

    public static QueueCancelResult failure(QueueCancelError error) {
        return new QueueCancelResult(
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

    public static QueueCancelResult retryLater(QueueCancelError error) {
        return new QueueCancelResult(
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
