package com.rpb.reservation.reservation.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReservationResourceAssignment(
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    int partySize,
    Instant reservedStartAt,
    Instant reservedEndAt,
    String customerName,
    String customerPhoneMasked,
    String resourceType,
    UUID resourceId,
    String resourceCode,
    UUID queueTicketId,
    Integer queueTicketNumber,
    String queueTicketStatus
) {

    public ReservationResourceAssignment {
        Objects.requireNonNull(reservationId, "reservation_id_required");
        if (reservationCode == null || reservationCode.isBlank()) {
            throw new IllegalArgumentException("reservation_code_required");
        }
        if (reservationStatus == null || reservationStatus.isBlank()) {
            throw new IllegalArgumentException("reservation_status_required");
        }
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resource_type_required");
        }
        Objects.requireNonNull(resourceId, "resource_id_required");
        reservationCode = reservationCode.trim();
        reservationStatus = reservationStatus.trim().toLowerCase();
        customerName = normalize(customerName);
        customerPhoneMasked = normalize(customerPhoneMasked);
        resourceType = resourceType.trim().toLowerCase();
        resourceCode = normalize(resourceCode);
        queueTicketStatus = normalize(queueTicketStatus);
    }

    public ReservationResourceTarget target() {
        return new ReservationResourceTarget(resourceType, resourceId);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
