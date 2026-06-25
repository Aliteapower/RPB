package com.rpb.reservation.queue.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.queue.value.QueueTicketId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueTicketRepositoryPort {

    Optional<QueueTicket> findById(StoreScope scope, QueueTicketId queueTicketId);

    List<QueueTicket> findActiveQueue(StoreScope scope, UUID queueGroupId, BusinessDate businessDate);

    Optional<QueueTicket> findNextCallable(StoreScope scope, UUID queueGroupId, BusinessDate businessDate);

    Optional<QueueTicket> findActiveByReservationId(StoreScope scope, UUID reservationId);

    boolean existsActiveSourceTicket(StoreScope scope, String sourceType, UUID sourceId);

    default QueueTicketListRows findQueueTicketList(
        StoreScope scope,
        QueueTicketStatus status,
        BusinessDate businessDate,
        int limit,
        int offset,
        String tableArea,
        Integer partySize,
        String phoneDigits
    ) {
        throw new UnsupportedOperationException("Queue ticket list read is not implemented by this repository.");
    }

    QueueTicket save(StoreScope scope, QueueTicket queueTicket);
}
