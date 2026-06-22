package com.rpb.reservation.queue.command;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.queue.value.QueueTicketNumber;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain command skeleton for queue ticket creation intent.
 *
 * <p>This command keeps queue ticket independent from reservation and walk-in flows.</p>
 */
public record CreateQueueTicketCommand(
        StoreScope storeScope,
        QueueTicketNumber queueTicketNumber,
        PartySize partySize,
        Optional<IdempotencyKey> idempotencyKey) {

    public CreateQueueTicketCommand {
        Objects.requireNonNull(storeScope, "storeScope must not be null");
        Objects.requireNonNull(queueTicketNumber, "queueTicketNumber must not be null");
        Objects.requireNonNull(partySize, "partySize must not be null");
        idempotencyKey = idempotencyKey == null ? Optional.empty() : idempotencyKey;
    }

    public String intentCode() {
        return "queue_ticket.create.command";
    }
}
