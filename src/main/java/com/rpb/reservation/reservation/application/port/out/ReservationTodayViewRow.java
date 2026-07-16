package com.rpb.reservation.reservation.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationTodayViewRow(
    UUID reservationId,
    String reservationCode,
    String status,
    int partySize,
    Instant reservedStartAt,
    Instant reservedEndAt,
    Instant holdUntilAt,
    LocalDate businessDate,
    String customerName,
    String customerNickname,
    String phoneE164,
    String note,
    UUID seatingId,
    String currentResourceType,
    UUID currentResourceId,
    String currentResourceCode,
    String assignedResourceType,
    UUID assignedResourceId,
    String assignedResourceCode,
    UUID queueTicketId,
    Integer queueTicketNumber,
    String queueTicketGroupCode,
    String queueTicketStatus
) {

    public ReservationTodayViewRow(
        UUID reservationId,
        String reservationCode,
        String status,
        int partySize,
        Instant reservedStartAt,
        Instant reservedEndAt,
        Instant holdUntilAt,
        LocalDate businessDate,
        String customerName,
        String customerNickname,
        String phoneE164,
        String note,
        UUID seatingId,
        String currentResourceType,
        UUID currentResourceId,
        String currentResourceCode
    ) {
        this(
            reservationId,
            reservationCode,
            status,
            partySize,
            reservedStartAt,
            reservedEndAt,
            holdUntilAt,
            businessDate,
            customerName,
            customerNickname,
            phoneE164,
            note,
            seatingId,
            currentResourceType,
            currentResourceId,
            currentResourceCode,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public ReservationTodayViewRow(
        UUID reservationId,
        String reservationCode,
        String status,
        int partySize,
        Instant reservedStartAt,
        Instant reservedEndAt,
        Instant holdUntilAt,
        LocalDate businessDate,
        String customerName,
        String customerNickname,
        String phoneE164,
        String note,
        UUID seatingId,
        String currentResourceType,
        UUID currentResourceId,
        String currentResourceCode,
        String assignedResourceType,
        UUID assignedResourceId,
        String assignedResourceCode,
        UUID queueTicketId,
        Integer queueTicketNumber,
        String queueTicketStatus
    ) {
        this(
            reservationId,
            reservationCode,
            status,
            partySize,
            reservedStartAt,
            reservedEndAt,
            holdUntilAt,
            businessDate,
            customerName,
            customerNickname,
            phoneE164,
            note,
            seatingId,
            currentResourceType,
            currentResourceId,
            currentResourceCode,
            assignedResourceType,
            assignedResourceId,
            assignedResourceCode,
            queueTicketId,
            queueTicketNumber,
            null,
            queueTicketStatus
        );
    }
}
