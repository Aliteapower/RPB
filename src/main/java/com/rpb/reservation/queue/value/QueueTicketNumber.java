package com.rpb.reservation.queue.value;

/**
 * Store + QueueGroup + business-date scoped queue ticket number.
 */
public record QueueTicketNumber(int value) {

    public QueueTicketNumber {
        if (value <= 0) {
            throw new IllegalArgumentException("queue_ticket_number_must_be_positive");
        }
    }
}
