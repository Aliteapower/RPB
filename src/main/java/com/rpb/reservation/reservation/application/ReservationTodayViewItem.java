package com.rpb.reservation.reservation.application;

import com.rpb.reservation.queue.application.QueueTicketDisplayNumbers;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationTodayViewItem(
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
    String phoneMasked,
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
    String queueTicketDisplayNumber,
    String queueTicketStatus
) {

    public ReservationTodayViewItem(
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
        String phoneMasked,
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
            phoneMasked,
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

    public ReservationTodayViewItem(
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
        String phoneMasked,
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
            phoneMasked,
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
            QueueTicketDisplayNumbers.fromGroupCode(null, queueTicketNumber),
            queueTicketStatus
        );
    }
}
