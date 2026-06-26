package com.rpb.reservation.queuedisplay.application;

import java.time.Instant;
import java.util.UUID;

public record QueueDisplayCurrentCall(
    UUID queueTicketId,
    Integer ticketNumber,
    String partySizeGroup,
    String customerName,
    Integer partySize,
    Instant calledAt,
    Instant holdUntilAt,
    String displayNumber,
    String customerDisplayName
) {
    public QueueDisplayCurrentCall(
        UUID queueTicketId,
        Integer ticketNumber,
        String partySizeGroup,
        String customerName,
        Integer partySize,
        Instant calledAt,
        Instant holdUntilAt
    ) {
        this(queueTicketId, ticketNumber, partySizeGroup, customerName, partySize, calledAt, holdUntilAt, null, null);
    }

    public QueueDisplayCurrentCall withDisplayValues(String displayNumber, String customerDisplayName) {
        return new QueueDisplayCurrentCall(
            queueTicketId,
            ticketNumber,
            partySizeGroup,
            customerName,
            partySize,
            calledAt,
            holdUntilAt,
            displayNumber,
            customerDisplayName
        );
    }
}
