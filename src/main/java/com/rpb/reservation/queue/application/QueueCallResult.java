package com.rpb.reservation.queue.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QueueCallResult(
    boolean success,
    QueueCallError error,
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    Instant calledAt,
    Instant holdUntilAt,
    boolean alreadyCalled,
    String idempotencyStatus,
    List<String> events,
    List<UUID> businessEventIds,
    List<UUID> stateTransitionLogIds,
    UUID auditLogId,
    boolean replayed,
    boolean retryLater
) {

    public QueueCallResult {
        events = events == null ? List.of() : List.copyOf(events);
        businessEventIds = businessEventIds == null ? List.of() : List.copyOf(businessEventIds);
        stateTransitionLogIds = stateTransitionLogIds == null ? List.of() : List.copyOf(stateTransitionLogIds);
    }

    public static QueueCallResult success(
        UUID queueTicketId,
        int queueTicketNumber,
        UUID reservationId,
        String reservationCode,
        Instant calledAt,
        Instant holdUntilAt,
        String idempotencyStatus,
        List<String> events,
        List<UUID> businessEventIds,
        List<UUID> stateTransitionLogIds,
        UUID auditLogId
    ) {
        return new QueueCallResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            "called",
            reservationId,
            reservationCode,
            reservationId == null ? null : "arrived",
            calledAt,
            holdUntilAt,
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

    public static QueueCallResult alreadyCalled(
        UUID queueTicketId,
        int queueTicketNumber,
        UUID reservationId,
        String reservationCode,
        Instant calledAt,
        Instant holdUntilAt,
        String idempotencyStatus
    ) {
        return new QueueCallResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            "called",
            reservationId,
            reservationCode,
            reservationId == null ? null : "arrived",
            calledAt,
            holdUntilAt,
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

    public static QueueCallResult replay(
        UUID queueTicketId,
        int queueTicketNumber,
        String queueTicketStatus,
        UUID reservationId,
        String reservationCode,
        String reservationStatus,
        Instant calledAt,
        Instant holdUntilAt,
        boolean alreadyCalled
    ) {
        return new QueueCallResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            queueTicketStatus,
            reservationId,
            reservationCode,
            reservationStatus,
            calledAt,
            holdUntilAt,
            alreadyCalled,
            "completed",
            List.of(),
            List.of(),
            List.of(),
            null,
            true,
            false
        );
    }

    public static QueueCallResult failure(QueueCallError error) {
        return new QueueCallResult(
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

    public static QueueCallResult retryLater(QueueCallError error) {
        return new QueueCallResult(
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
