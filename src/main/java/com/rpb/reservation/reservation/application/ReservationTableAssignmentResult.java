package com.rpb.reservation.reservation.application;

import java.util.UUID;

public record ReservationTableAssignmentResult(
    boolean success,
    ReservationTableAssignmentError error,
    UUID reservationId,
    UUID tableId,
    String tableCode,
    String assignmentStatus,
    String idempotencyStatus,
    UUID businessEventId,
    UUID auditLogId,
    boolean replayed,
    boolean retryLater
) {
    public static ReservationTableAssignmentResult success(
        UUID reservationId,
        UUID tableId,
        String tableCode,
        String idempotencyStatus,
        UUID businessEventId,
        UUID auditLogId
    ) {
        return new ReservationTableAssignmentResult(
            true, null, reservationId, tableId, tableCode, "active", idempotencyStatus,
            businessEventId, auditLogId, false, false
        );
    }

    public static ReservationTableAssignmentResult replay(UUID reservationId, UUID tableId, String tableCode) {
        return new ReservationTableAssignmentResult(
            true, null, reservationId, tableId, tableCode, "active", "completed",
            null, null, true, false
        );
    }

    public static ReservationTableAssignmentResult failure(ReservationTableAssignmentError error) {
        return new ReservationTableAssignmentResult(
            false, error, null, null, null, null, null, null, null, false, false
        );
    }

    public static ReservationTableAssignmentResult retryLater(ReservationTableAssignmentError error) {
        return new ReservationTableAssignmentResult(
            false, error, null, null, null, null, null, null, null, false, true
        );
    }
}
