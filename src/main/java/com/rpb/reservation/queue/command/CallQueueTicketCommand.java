package com.rpb.reservation.queue.command;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.queue.value.QueueTicketId;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain command skeleton for queue calling intent.
 *
 * <p>This command does not implement call flow, notification, API, or persistence behavior.</p>
 */
public record CallQueueTicketCommand(
        StoreScope storeScope,
        QueueTicketId queueTicketId,
        Optional<IdempotencyKey> idempotencyKey) {

    public CallQueueTicketCommand {
        Objects.requireNonNull(storeScope, "storeScope must not be null");
        Objects.requireNonNull(queueTicketId, "queueTicketId must not be null");
        idempotencyKey = idempotencyKey == null ? Optional.empty() : idempotencyKey;
    }

    public String intentCode() {
        return "queue_ticket.call.command";
    }
}
