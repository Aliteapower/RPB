package com.rpb.reservation.queue.application;

import java.time.Instant;
import java.util.UUID;

public record QueueTicketListItem(
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketDisplayNumber,
    String queueTicketStatus,
    int partySize,
    String partySizeGroup,
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    String customerName,
    String customerPhoneMasked,
    String assignedResourceType,
    UUID assignedResourceId,
    String assignedResourceCode,
    String assignedResourceGroupType,
    String assignedResourceLabel,
    String assignedResourceAreaName,
    Instant createdAt,
    Instant calledAt,
    Instant holdUntilAt,
    Instant expiresAt
) {

    public QueueTicketListItem(
        UUID queueTicketId,
        int queueTicketNumber,
        String queueTicketStatus,
        int partySize,
        String partySizeGroup,
        UUID reservationId,
        String reservationCode,
        String reservationStatus,
        String customerName,
        String customerPhoneMasked,
        Instant createdAt,
        Instant calledAt,
        Instant holdUntilAt,
        Instant expiresAt
    ) {
        this(
            queueTicketId,
            queueTicketNumber,
            QueueTicketDisplayNumbers.fromGroupCode(partySizeGroup, queueTicketNumber),
            queueTicketStatus,
            partySize,
            partySizeGroup,
            reservationId,
            reservationCode,
            reservationStatus,
            customerName,
            customerPhoneMasked,
            null,
            null,
            null,
            null,
            null,
            null,
            createdAt,
            calledAt,
            holdUntilAt,
            expiresAt
        );
    }

    public QueueTicketListItem(
        UUID queueTicketId,
        int queueTicketNumber,
        String queueTicketStatus,
        int partySize,
        String partySizeGroup,
        UUID reservationId,
        String reservationCode,
        String reservationStatus,
        String customerName,
        String customerPhoneMasked,
        String assignedResourceType,
        UUID assignedResourceId,
        String assignedResourceCode,
        String assignedResourceAreaName,
        Instant createdAt,
        Instant calledAt,
        Instant holdUntilAt,
        Instant expiresAt
    ) {
        this(
            queueTicketId,
            queueTicketNumber,
            QueueTicketDisplayNumbers.fromGroupCode(partySizeGroup, queueTicketNumber),
            queueTicketStatus,
            partySize,
            partySizeGroup,
            reservationId,
            reservationCode,
            reservationStatus,
            customerName,
            customerPhoneMasked,
            assignedResourceType,
            assignedResourceId,
            assignedResourceCode,
            null,
            null,
            assignedResourceAreaName,
            createdAt,
            calledAt,
            holdUntilAt,
            expiresAt
        );
    }
}
