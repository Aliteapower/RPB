package com.rpb.reservation.queue.application;

public record QueueTicketListPage(
    int limit,
    int offset,
    int total
) {
}
