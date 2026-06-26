package com.rpb.reservation.queuedisplay.application;

public record QueueDisplayWaitingPreviewItem(
    Integer ticketNumber,
    String partySizeGroup,
    String customerName,
    Integer partySize,
    String displayNumber,
    String customerDisplayName
) {
    public QueueDisplayWaitingPreviewItem(Integer ticketNumber, String partySizeGroup, String customerName, Integer partySize) {
        this(ticketNumber, partySizeGroup, customerName, partySize, null, null);
    }

    public QueueDisplayWaitingPreviewItem withDisplayValues(String displayNumber, String customerDisplayName) {
        return new QueueDisplayWaitingPreviewItem(ticketNumber, partySizeGroup, customerName, partySize, displayNumber, customerDisplayName);
    }
}
