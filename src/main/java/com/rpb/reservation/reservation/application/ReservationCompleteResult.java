package com.rpb.reservation.reservation.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationCompleteResult(
    boolean success,
    ReservationCompleteError error,
    boolean retryLater,
    UUID reservationId,
    String reservationCode,
    String status,
    Instant completedAt,
    UUID seatingId,
    String seatingStatus,
    String idempotencyStatus,
    boolean replayed,
    boolean alreadyCompleted,
    List<String> events,
    List<UUID> eventIds,
    List<UUID> transitionIds,
    UUID auditLogId
) {
    public ReservationCompleteResult {
        events = events == null ? List.of() : List.copyOf(events);
        eventIds = eventIds == null ? List.of() : List.copyOf(eventIds);
        transitionIds = transitionIds == null ? List.of() : List.copyOf(transitionIds);
    }

    public static ReservationCompleteResult success(
        UUID reservationId,
        String reservationCode,
        String status,
        Instant completedAt,
        UUID seatingId,
        String seatingStatus,
        String idempotencyStatus,
        List<String> events,
        List<UUID> eventIds,
        List<UUID> transitionIds,
        UUID auditLogId
    ) {
        return new ReservationCompleteResult(
            true,
            null,
            false,
            reservationId,
            reservationCode,
            status,
            completedAt,
            seatingId,
            seatingStatus,
            idempotencyStatus,
            false,
            false,
            events,
            eventIds,
            transitionIds,
            auditLogId
        );
    }

    public static ReservationCompleteResult alreadyCompleted(
        UUID reservationId,
        String reservationCode,
        Instant completedAt,
        UUID seatingId,
        String seatingStatus,
        String idempotencyStatus
    ) {
        return new ReservationCompleteResult(
            true,
            null,
            false,
            reservationId,
            reservationCode,
            "completed",
            completedAt,
            seatingId,
            seatingStatus,
            idempotencyStatus,
            false,
            true,
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    public static ReservationCompleteResult replay(
        UUID reservationId,
        String reservationCode,
        String status,
        Instant completedAt,
        UUID seatingId,
        String seatingStatus,
        boolean alreadyCompleted
    ) {
        return new ReservationCompleteResult(
            true,
            null,
            false,
            reservationId,
            reservationCode,
            status,
            completedAt,
            seatingId,
            seatingStatus,
            "completed",
            true,
            alreadyCompleted,
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    public static ReservationCompleteResult failure(ReservationCompleteError error) {
        return new ReservationCompleteResult(
            false,
            error,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            "failed",
            false,
            false,
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    public static ReservationCompleteResult retryLater(ReservationCompleteError error) {
        return new ReservationCompleteResult(
            false,
            error,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            "started",
            false,
            false,
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }
}
