package com.rpb.reservation.queue.command;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.queue.value.QueueTicketId;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain command skeleton for queue rejoin intent.
 *
 * <p>The actual rejoin eligibility decision belongs to queue policies and application orchestration.</p>
 */
public record RejoinQueueTicketCommand(
        StoreScope storeScope,
        QueueTicketId queueTicketId,
        Optional<IdempotencyKey> idempotencyKey) {

    public RejoinQueueTicketCommand {
        Objects.requireNonNull(storeScope, "storeScope must not be null");
        Objects.requireNonNull(queueTicketId, "queueTicketId must not be null");
        idempotencyKey = idempotencyKey == null ? Optional.empty() : idempotencyKey;
    }

    public String intentCode() {
        return "queue_ticket.rejoin.command";
    }
}
