package com.rpb.reservation.queue.application.rule;

import com.rpb.reservation.queue.application.QueueSkipError;
import com.rpb.reservation.queue.status.QueueTicketStatus;

public class QueueSkipRule {

    public QueueSkipError validateFreshSkip(QueueTicketStatus status) {
        if (status == QueueTicketStatus.CALLED) {
            return null;
        }
        return QueueSkipError.QUEUE_TICKET_STATUS_NOT_CALLED;
    }
}
