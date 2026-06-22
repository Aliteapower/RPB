package com.rpb.reservation.reservation.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationCheckInResult(
    boolean success,
    ReservationCheckInError error,
    UUID reservationId,
    String reservationCode,
    String status,
    Instant arrivedAt,
    List<String> events,
    String idempotencyStatus,
    List<UUID> businessEventIds,
    List<UUID> stateTransitionLogIds,
    UUID auditLogId,
    boolean alreadyArrived,
    boolean replayed,
    boolean retryLater
) {

    public ReservationCheckInResult {
        events = events == null ? List.of() : List.copyOf(events);
        businessEventIds = businessEventIds == null ? List.of() : List.copyOf(businessEventIds);
        stateTransitionLogIds = stateTransitionLogIds == null ? List.of() : List.copyOf(stateTransitionLogIds);
    }

    public static ReservationCheckInResult success(
        UUID reservationId,
        String reservationCode,
        String status,
        Instant arrivedAt,
        String idempotencyStatus,
        List<String> events,
        List<UUID> businessEventIds,
        List<UUID> stateTransitionLogIds,
        UUID auditLogId
    ) {
        return new ReservationCheckInResult(
            true,
            null,
            reservationId,
            reservationCode,
            status,
            arrivedAt,
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

    public static ReservationCheckInResult alreadyArrived(
        UUID reservationId,
        String reservationCode,
        Instant arrivedAt,
        String idempotencyStatus
    ) {
        return new ReservationCheckInResult(
            true,
            null,
            reservationId,
            reservationCode,
            "arrived",
            arrivedAt,
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

    public static ReservationCheckInResult replay(
        UUID reservationId,
        String reservationCode,
        String status,
        Instant arrivedAt,
        boolean alreadyArrived
    ) {
        return new ReservationCheckInResult(
            true,
            null,
            reservationId,
            reservationCode,
            status,
            arrivedAt,
            List.of(),
            "completed",
            List.of(),
            List.of(),
            null,
            alreadyArrived,
            true,
            false
        );
    }

    public static ReservationCheckInResult failure(ReservationCheckInError error) {
        return new ReservationCheckInResult(
            false,
            error,
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

    public static ReservationCheckInResult retryLater(ReservationCheckInError error) {
        return new ReservationCheckInResult(
            false,
            error,
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
