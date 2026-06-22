package com.rpb.reservation.queue.status;

/**
 * QueueTicket statuses confirmed by governance and migration constraints.
 */
public enum QueueTicketStatus {
    WAITING("waiting"),
    CALLED("called"),
    SKIPPED("skipped"),
    REJOINED("rejoined"),
    SEATED("seated"),
    CANCELLED("cancelled"),
    EXPIRED("expired");

    private final String code;

    QueueTicketStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
