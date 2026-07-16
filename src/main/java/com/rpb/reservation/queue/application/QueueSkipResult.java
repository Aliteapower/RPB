package com.rpb.reservation.queue.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QueueSkipResult(
    boolean success,
    QueueSkipError error,
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    Instant skippedAt,
    boolean alreadySkipped,
    String idempotencyStatus,
    List<String> events,
    List<UUID> businessEventIds,
    List<UUID> stateTransitionLogIds,
    UUID auditLogId,
    boolean replayed,
    boolean retryLater
) {

    public QueueSkipResult {
        events = events == null ? List.of() : List.copyOf(events);
        businessEventIds = businessEventIds == null ? List.of() : List.copyOf(businessEventIds);
        stateTransitionLogIds = stateTransitionLogIds == null ? List.of() : List.copyOf(stateTransitionLogIds);
    }

    public static QueueSkipResult success(
        UUID queueTicketId,
        int queueTicketNumber,
        UUID reservationId,
        String reservationCode,
        Instant skippedAt,
        String idempotencyStatus,
        List<String> events,
        List<UUID> businessEventIds,
        List<UUID> stateTransitionLogIds,
        UUID auditLogId
    ) {
        return new QueueSkipResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            "skipped",
            reservationId,
            reservationCode,
            reservationId == null ? null : "arrived",
            skippedAt,
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

    public static QueueSkipResult alreadySkipped(
        UUID queueTicketId,
        int queueTicketNumber,
        UUID reservationId,
        String reservationCode,
        Instant skippedAt,
        String idempotencyStatus
    ) {
        return new QueueSkipResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            "skipped",
            reservationId,
            reservationCode,
            reservationId == null ? null : "arrived",
            skippedAt,
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

    public static QueueSkipResult replay(
        UUID queueTicketId,
        int queueTicketNumber,
        String queueTicketStatus,
        UUID reservationId,
        String reservationCode,
        String reservationStatus,
        Instant skippedAt,
        boolean alreadySkipped
    ) {
        return new QueueSkipResult(
            true,
            null,
            queueTicketId,
            queueTicketNumber,
            queueTicketStatus,
            reservationId,
            reservationCode,
            reservationStatus,
            skippedAt,
            alreadySkipped,
            "completed",
            List.of(),
            List.of(),
            List.of(),
            null,
            true,
            false
        );
    }

    public static QueueSkipResult failure(QueueSkipError error) {
        return new QueueSkipResult(
            false,
            error,
            null,
            0,
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

    public static QueueSkipResult retryLater(QueueSkipError error) {
        return new QueueSkipResult(
            false,
            error,
            null,
            0,
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
