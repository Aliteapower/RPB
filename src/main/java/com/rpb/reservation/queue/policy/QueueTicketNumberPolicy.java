package com.rpb.reservation.queue.policy;

import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.value.QueueTicketNumber;
import java.util.List;

public class QueueTicketNumberPolicy {

    public QueueTicketNumber nextTicketNumber(List<QueueTicket> activeQueue) {
        int max = safe(activeQueue).stream()
            .map(QueueTicket::ticketNumber)
            .mapToInt(QueueTicketNumber::value)
            .max()
            .orElse(0);
        if (max == Integer.MAX_VALUE) {
            throw new QueueTicketNumberConflictException("queue_ticket_number_exhausted");
        }
        return new QueueTicketNumber(max + 1);
    }

    public int nextQueuePosition(List<QueueTicket> activeQueue) {
        int max = safe(activeQueue).stream()
            .map(QueueTicket::queuePosition)
            .filter(position -> position != null && position > 0)
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0);
        if (max == Integer.MAX_VALUE) {
            throw new QueueTicketNumberConflictException("queue_position_exhausted");
        }
        return max + 1;
    }

    private static List<QueueTicket> safe(List<QueueTicket> activeQueue) {
        return activeQueue == null ? List.of() : activeQueue;
    }
}
