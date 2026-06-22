package com.rpb.reservation.queue.value;

import java.util.Objects;
import java.util.UUID;

/**
 * QueueTicket identity value.
 */
public record QueueTicketId(UUID value) {

    public QueueTicketId {
        Objects.requireNonNull(value, "queue_ticket_id_required");
    }
}
