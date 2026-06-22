package com.rpb.reservation.queue.application.rule;

import com.rpb.reservation.queue.application.SeatingFromCalledQueueError;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.status.QueueTicketStatus;

public class QueueTicketSeatRule {

    public SeatingFromCalledQueueError validateFreshSeating(QueueTicket queueTicket) {
        if (queueTicket.status() == QueueTicketStatus.CALLED) {
            if (queueTicket.calledAt() == null || queueTicket.expiresAt() == null) {
                return SeatingFromCalledQueueError.QUEUE_TICKET_CALL_EVIDENCE_INCOMPLETE;
            }
            if (queueTicket.reservationId() == null || queueTicket.walkInId() != null) {
                return SeatingFromCalledQueueError.QUEUE_TICKET_SOURCE_NOT_RESERVATION;
            }
            return null;
        }
        if (queueTicket.status() == QueueTicketStatus.CANCELLED) {
            return SeatingFromCalledQueueError.QUEUE_TICKET_CANNOT_SEAT_CANCELLED;
        }
        if (queueTicket.status() == QueueTicketStatus.EXPIRED) {
            return SeatingFromCalledQueueError.QUEUE_TICKET_CANNOT_SEAT_EXPIRED;
        }
        return SeatingFromCalledQueueError.QUEUE_TICKET_STATUS_NOT_CALLED;
    }
}
