package com.rpb.reservation.queue.application.rule;

import com.rpb.reservation.queue.application.QueueCallError;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.status.QueueTicketStatus;

public class QueueCallRule {

    public QueueCallError validateFreshCall(QueueTicketStatus status) {
        if (status == QueueTicketStatus.WAITING) {
            return null;
        }
        if (status == QueueTicketStatus.SEATED) {
            return QueueCallError.QUEUE_TICKET_CANNOT_CALL_SEATED;
        }
        if (status == QueueTicketStatus.CANCELLED) {
            return QueueCallError.QUEUE_TICKET_CANNOT_CALL_CANCELLED;
        }
        if (status == QueueTicketStatus.EXPIRED) {
            return QueueCallError.QUEUE_TICKET_CANNOT_CALL_EXPIRED;
        }
        return QueueCallError.QUEUE_TICKET_STATUS_NOT_WAITING;
    }

    public QueueCallError validateAlreadyCalledEvidence(QueueTicket queueTicket) {
        if (queueTicket.calledAt() != null && queueTicket.expiresAt() != null) {
            return null;
        }
        return QueueCallError.QUEUE_CALL_EVIDENCE_INCOMPLETE;
    }
}
