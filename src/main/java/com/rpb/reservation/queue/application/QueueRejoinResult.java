package com.rpb.reservation.queue.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QueueRejoinResult(
    boolean success,
    QueueRejoinError error,
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
    Integer queuePosition,
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    Instant rejoinedAt,
    boolean alreadyRejoined,
    String idempotencyStatus,
    List<String> events,
    List<UUID> businessEventIds,
    List<UUID> stateTransitionLogIds,
    UUID auditLogId,
    boolean replayed,
    boolean retryLater
) {

    public QueueRejoinResult {
        events = events == null ? List.of() : List.copyOf(events);
        businessEventIds = businessEventIds == null ? List.of() : List.copyOf(businessEventIds);
        stateTransitionLogIds = stateTransitionLogIds == null ? List.of() : List.copyOf(stateTransitionLogIds);
    }

    public static QueueRejoinResult success(
        UUID queueTicketId,
        int queueTicketNumber,
        Integer queuePosition,
        UUID reservationId,
        String reservationCode,
        Instant rejoinedAt,
        String idempotencyStatus,
        List<String> events,
        List<UUID> businessEventIds,
        List<UUID> stateTransitionLogIds,
        UUID auditLogId
    ) {
        return new QueueRejoinResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            "waiting",
            queuePosition,
            reservationId,
            reservationCode,
            reservationId == null ? null : "arrived",
            rejoinedAt,
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

    public static QueueRejoinResult alreadyRejoined(
        UUID queueTicketId,
        int queueTicketNumber,
        Integer queuePosition,
        UUID reservationId,
        String reservationCode,
        Instant rejoinedAt,
        String idempotencyStatus
    ) {
        return new QueueRejoinResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            "waiting",
            queuePosition,
            reservationId,
            reservationCode,
            reservationId == null ? null : "arrived",
            rejoinedAt,
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

    public static QueueRejoinResult replay(
        UUID queueTicketId,
        int queueTicketNumber,
        String queueTicketStatus,
        Integer queuePosition,
        UUID reservationId,
        String reservationCode,
        String reservationStatus,
        Instant rejoinedAt,
        boolean alreadyRejoined
    ) {
        return new QueueRejoinResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            queueTicketStatus,
            queuePosition,
            reservationId,
            reservationCode,
            reservationStatus,
            rejoinedAt,
            alreadyRejoined,
            "completed",
            List.of(),
            List.of(),
            List.of(),
            null,
            true,
            false
        );
    }

    public static QueueRejoinResult failure(QueueRejoinError error) {
        return new QueueRejoinResult(
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

    public static QueueRejoinResult retryLater(QueueRejoinError error) {
        return new QueueRejoinResult(
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
