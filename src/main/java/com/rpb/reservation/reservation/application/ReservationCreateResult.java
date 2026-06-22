package com.rpb.reservation.reservation.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReservationCreateResult(
    boolean success,
    ReservationCreateError error,
    UUID reservationId,
    UUID customerId,
    String reservationCode,
    int partySize,
    LocalDate businessDate,
    Instant reservedStartAt,
    Instant reservedEndAt,
    Instant holdUntilAt,
    String status,
    String idempotencyStatus,
    List<UUID> businessEventIds,
    List<UUID> stateTransitionLogIds,
    UUID auditLogId,
    boolean replayed,
    boolean retryLater
) {

    public ReservationCreateResult {
        businessEventIds = businessEventIds == null ? List.of() : List.copyOf(businessEventIds);
        stateTransitionLogIds = stateTransitionLogIds == null ? List.of() : List.copyOf(stateTransitionLogIds);
    }

    public static ReservationCreateResult success(
        UUID reservationId,
        UUID customerId,
        String reservationCode,
        int partySize,
        LocalDate businessDate,
        Instant reservedStartAt,
        Instant reservedEndAt,
        Instant holdUntilAt,
        String status,
        String idempotencyStatus,
        List<UUID> businessEventIds,
        List<UUID> stateTransitionLogIds,
        UUID auditLogId
    ) {
        return new ReservationCreateResult(
            true,
            null,
            reservationId,
            customerId,
            reservationCode,
            partySize,
            businessDate,
            reservedStartAt,
            reservedEndAt,
            holdUntilAt,
            status,
            idempotencyStatus,
            businessEventIds,
            stateTransitionLogIds,
            auditLogId,
            false,
            false
        );
    }

    public static ReservationCreateResult replay(
        UUID reservationId,
        UUID customerId,
        String reservationCode,
        int partySize,
        LocalDate businessDate,
        Instant reservedStartAt,
        Instant reservedEndAt,
        Instant holdUntilAt,
        String status
    ) {
        return new ReservationCreateResult(
            true,
            null,
            reservationId,
            customerId,
            reservationCode,
            partySize,
            businessDate,
            reservedStartAt,
            reservedEndAt,
            holdUntilAt,
            status,
            "completed",
            List.of(),
            List.of(),
            null,
            true,
            false
        );
    }

    public static ReservationCreateResult failure(ReservationCreateError error) {
        return new ReservationCreateResult(
            false,
            error,
            null,
            null,
            null,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            null,
            false,
            false
        );
    }

    public static ReservationCreateResult retryLater(ReservationCreateError error) {
        return new ReservationCreateResult(
            false,
            error,
            null,
            null,
            null,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            null,
            false,
            true
        );
    }
}
