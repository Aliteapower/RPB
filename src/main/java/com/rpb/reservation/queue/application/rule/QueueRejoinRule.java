package com.rpb.reservation.queue.application.rule;

import com.rpb.reservation.queue.application.QueueRejoinError;
import com.rpb.reservation.queue.status.QueueTicketStatus;

public class QueueRejoinRule {

    public QueueRejoinError validateFreshRejoin(QueueTicketStatus status) {
        if (status == QueueTicketStatus.SKIPPED) {
            return null;
        }
        return QueueRejoinError.QUEUE_TICKET_STATUS_NOT_SKIPPED;
    }
}
