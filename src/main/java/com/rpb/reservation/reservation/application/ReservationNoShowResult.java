package com.rpb.reservation.reservation.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationNoShowResult(
    boolean success,
    ReservationNoShowError error,
    boolean retryLater,
    UUID reservationId,
    String reservationCode,
    String status,
    Instant noShowAt,
    String noShowReasonCode,
    String idempotencyStatus,
    boolean replayed,
    boolean alreadyNoShow,
    List<String> events,
    List<UUID> eventIds,
    List<UUID> transitionIds,
    UUID auditLogId
) {
    public ReservationNoShowResult {
        events = events == null ? List.of() : List.copyOf(events);
        eventIds = eventIds == null ? List.of() : List.copyOf(eventIds);
        transitionIds = transitionIds == null ? List.of() : List.copyOf(transitionIds);
    }

    public static ReservationNoShowResult success(
        UUID reservationId,
        String reservationCode,
        String status,
        Instant noShowAt,
        String noShowReasonCode,
        String idempotencyStatus,
        List<String> events,
        List<UUID> eventIds,
        List<UUID> transitionIds,
        UUID auditLogId
    ) {
        return new ReservationNoShowResult(
            true,
            null,
            false,
            reservationId,
            reservationCode,
            status,
            noShowAt,
            noShowReasonCode,
            idempotencyStatus,
            false,
            false,
            events,
            eventIds,
            transitionIds,
            auditLogId
        );
    }

    public static ReservationNoShowResult alreadyNoShow(
        UUID reservationId,
        String reservationCode,
        Instant noShowAt,
        String noShowReasonCode,
        String idempotencyStatus
    ) {
        return new ReservationNoShowResult(
            true,
            null,
            false,
            reservationId,
            reservationCode,
            "no_show",
            noShowAt,
            noShowReasonCode,
            idempotencyStatus,
            false,
            true,
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    public static ReservationNoShowResult replay(
        UUID reservationId,
        String reservationCode,
        String status,
        Instant noShowAt,
        String noShowReasonCode,
        boolean alreadyNoShow
    ) {
        return new ReservationNoShowResult(
            true,
            null,
            false,
            reservationId,
            reservationCode,
            status,
            noShowAt,
            noShowReasonCode,
            "completed",
            true,
            alreadyNoShow,
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    public static ReservationNoShowResult failure(ReservationNoShowError error) {
        return new ReservationNoShowResult(
            false,
            error,
            false,
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

    public static ReservationNoShowResult retryLater(ReservationNoShowError error) {
        return new ReservationNoShowResult(
            false,
            error,
            true,
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
