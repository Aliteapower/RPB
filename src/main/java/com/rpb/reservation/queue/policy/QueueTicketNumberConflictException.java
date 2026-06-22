package com.rpb.reservation.queue.policy;

public class QueueTicketNumberConflictException extends RuntimeException {

    public QueueTicketNumberConflictException(String message) {
        super(message);
    }
}
