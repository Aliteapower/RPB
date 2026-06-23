package com.rpb.reservation.reservation.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationCancelResult(
    boolean success,
    ReservationCancelError error,
    UUID reservationId,
    String reservationCode,
    String status,
    Instant cancelledAt,
    String cancellationReasonCode,
    List<String> events,
    String idempotencyStatus,
    List<UUID> businessEventIds,
    List<UUID> stateTransitionLogIds,
    UUID auditLogId,
    boolean alreadyCancelled,
    boolean replayed,
    boolean retryLater
) {

    public ReservationCancelResult {
        events = events == null ? List.of() : List.copyOf(events);
        businessEventIds = businessEventIds == null ? List.of() : List.copyOf(businessEventIds);
        stateTransitionLogIds = stateTransitionLogIds == null ? List.of() : List.copyOf(stateTransitionLogIds);
    }

    public static ReservationCancelResult success(
        UUID reservationId,
        String reservationCode,
        String status,
        Instant cancelledAt,
        String cancellationReasonCode,
        String idempotencyStatus,
        List<String> events,
        List<UUID> businessEventIds,
        List<UUID> stateTransitionLogIds,
        UUID auditLogId
    ) {
        return new ReservationCancelResult(
            true,
            null,
            reservationId,
            reservationCode,
            status,
            cancelledAt,
            cancellationReasonCode,
            events,
            idempotencyStatus,
            businessEventIds,
            stateTransitionLogIds,
            auditLogId,
            false,
            false,
            false
        );
    }

    public static ReservationCancelResult alreadyCancelled(
        UUID reservationId,
        String reservationCode,
        Instant cancelledAt,
        String cancellationReasonCode,
        String idempotencyStatus
    ) {
        return new ReservationCancelResult(
            true,
            null,
            reservationId,
            reservationCode,
            "cancelled",
            cancelledAt,
            cancellationReasonCode,
            List.of(),
            idempotencyStatus,
            List.of(),
            List.of(),
            null,
            true,
            false,
            false
        );
    }

    public static ReservationCancelResult replay(
        UUID reservationId,
        String reservationCode,
        String status,
        Instant cancelledAt,
        String cancellationReasonCode,
        boolean alreadyCancelled
    ) {
        return new ReservationCancelResult(
            true,
            null,
            reservationId,
            reservationCode,
            status,
            cancelledAt,
            cancellationReasonCode,
            List.of(),
            "completed",
            List.of(),
            List.of(),
            null,
            alreadyCancelled,
            true,
            false
        );
    }

    public static ReservationCancelResult failure(ReservationCancelError error) {
        return new ReservationCancelResult(
            false,
            error,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            null,
            false,
            false,
            false
        );
    }

    public static ReservationCancelResult retryLater(ReservationCancelError error) {
        return new ReservationCancelResult(
            false,
            error,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            null,
            false,
            false,
            true
        );
    }
}
