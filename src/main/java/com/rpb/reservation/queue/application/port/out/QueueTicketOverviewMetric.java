package com.rpb.reservation.queue.application.port.out;

public record QueueTicketOverviewMetric(
    String status,
    String partySizeGroup,
    long ticketCount,
    int partySizeTotal
) {
}
